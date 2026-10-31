package dev.kfanter.mcdcbridge.discord;

import dev.kfanter.mcdcbridge.config.BridgeConfig;
import dev.kfanter.mcdcbridge.link.LinkStore;
import dev.kfanter.mcdcbridge.whitelist.WhitelistService;
import dev.kfanter.mcdcbridge.whitelist.WhitelistService.Outcome;
import dev.kfanter.mcdcbridge.whitelist.WhitelistService.Result;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.bukkit.configuration.ConfigurationSection;

/**
 * The whole Discord side of the whitelist flow:
 * panel button -> modal -> review embed (approve/deny buttons) -> whitelist.
 * All state lives in the component ids, so pending requests survive restarts.
 */
public final class WhitelistListener extends ListenerAdapter {

    private static final String REQUEST = "whitelist:request";
    private static final String MODAL = "whitelist:modal";
    private static final String APPROVE = "whitelist:approve:";
    private static final String DENY = "whitelist:deny:";
    private static final String NAME_INPUT = "player";
    private static final Pattern VALID_NAME = Pattern.compile("\\w{3,16}");

    private final BridgeConfig config;
    private final EmbedFactory embeds;
    private final WhitelistService whitelist;
    private final LinkStore links;

    public WhitelistListener(BridgeConfig config, EmbedFactory embeds, WhitelistService whitelist, LinkStore links) {
        this.config = config;
        this.embeds = embeds;
        this.whitelist = whitelist;
        this.links = links;
    }

    /** The message with the "request whitelist" button. */
    public MessageCreateData panel() {
        return new MessageCreateBuilder()
                .setEmbeds(embeds.embed("panel", Map.of()))
                .addActionRow(embeds.button("request", REQUEST))
                .build();
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (id.equals(REQUEST)) {
            event.replyModal(modal()).queue();
        } else if (id.startsWith(APPROVE)) {
            review(event, true);
        } else if (id.startsWith(DENY)) {
            review(event, false);
        }
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (!event.getModalId().equals(MODAL)) {
            return;
        }
        String player = event.getValue(NAME_INPUT).getAsString().trim();
        Map<String, String> placeholders = placeholders(player, event.getUser().getId(), null);
        if (!VALID_NAME.matcher(player).matches()) {
            event.replyEmbeds(embeds.embed("invalid-name", placeholders)).setEphemeral(true).queue();
            return;
        }
        if (hasRole(event.getMember(), config.banRoleId()) || hasRole(event.getMember(), config.tempbanRoleId())) {
            event.replyEmbeds(embeds.embed("banned", placeholders)).setEphemeral(true).queue();
            return;
        }
        event.deferReply(true).queue();
        if (config.autoApprove()) {
            whitelist.add(player).thenAccept(outcome -> {
                onWhitelisted(event.getGuild(), event.getUser().getId(), player, outcome);
                edit(event.getHook(), resultEmbed(outcome.result()), placeholders);
            });
        } else {
            sendForReview(event.getHook(), event.getJDA().getTextChannelById(config.reviewChannelId()),
                    player, event.getUser().getId(), placeholders);
        }
    }

    private void sendForReview(InteractionHook hook, TextChannel channel, String player, String userId,
                               Map<String, String> placeholders) {
        if (channel == null) {
            edit(hook, "error", placeholders);
            return;
        }
        String payload = userId + ":" + player;
        channel.sendMessageEmbeds(embeds.embed("review", placeholders))
                .addActionRow(embeds.button("approve", APPROVE + payload), embeds.button("deny", DENY + payload))
                .queue(sent -> edit(hook, "request-sent", placeholders),
                        failure -> edit(hook, "error", placeholders));
    }

    private void review(ButtonInteractionEvent event, boolean approve) {
        // Component id: whitelist:<approve|deny>:<requesterId>:<player>
        String[] parts = event.getComponentId().split(":", 4);
        Map<String, String> placeholders = placeholders(parts[3], parts[2], event.getUser().getId());
        if (!isStaff(event.getMember())) {
            event.replyEmbeds(embeds.embed("no-permission", placeholders)).setEphemeral(true).queue();
            return;
        }
        if (!approve) {
            event.editMessageEmbeds(embeds.embed("denied", placeholders)).setComponents(List.of()).queue();
            return;
        }
        event.deferEdit().queue();
        whitelist.add(parts[3]).thenAccept(outcome -> {
            onWhitelisted(event.getGuild(), parts[2], parts[3], outcome);
            event.getHook().editOriginalEmbeds(embeds.embed(resultEmbed(outcome.result()), placeholders))
                    .setComponents(List.of()).queue();
        });
    }

    /** Remembers which Minecraft account the Discord user owns and hands out the member role. */
    private void onWhitelisted(Guild guild, String userId, String player, Outcome outcome) {
        if (outcome.result() == Result.NOT_FOUND) {
            return;
        }
        links.link(userId, outcome.uuid(), player, guild.getId());
        Role role = config.memberRoleId().isEmpty() ? null : guild.getRoleById(config.memberRoleId());
        if (role != null) {
            guild.addRoleToMember(UserSnowflake.fromId(userId), role).queue();
        }
    }

    private boolean isStaff(Member member) {
        if (member == null) {
            return false;
        }
        String roleId = config.staffRoleId();
        return roleId.isEmpty() ? member.hasPermission(Permission.MANAGE_SERVER) : hasRole(member, roleId);
    }

    private static boolean hasRole(Member member, String roleId) {
        return member != null && !roleId.isEmpty()
                && member.getRoles().stream().anyMatch(role -> role.getId().equals(roleId));
    }

    private Modal modal() {
        ConfigurationSection section = config.section("modal");
        TextInput input = TextInput.create(NAME_INPUT, section.getString("label", "Minecraft name"), TextInputStyle.SHORT)
                .setPlaceholder(section.getString("placeholder"))
                .setMinLength(3)
                .setMaxLength(16)
                .setRequired(true)
                .build();
        return Modal.create(MODAL, section.getString("title", "Whitelist"))
                .addComponents(ActionRow.of(input))
                .build();
    }

    private void edit(InteractionHook hook, String embedKey, Map<String, String> placeholders) {
        hook.editOriginalEmbeds(embeds.embed(embedKey, placeholders)).queue();
    }

    private static String resultEmbed(Result result) {
        return switch (result) {
            case ADDED -> "approved";
            case ALREADY_WHITELISTED -> "already-whitelisted";
            case NOT_FOUND -> "not-found";
        };
    }

    private static Map<String, String> placeholders(String player, String requesterId, String reviewerId) {
        return Map.of(
                "player", player,
                "user", "<@" + requesterId + ">",
                "reviewer", reviewerId == null ? "" : "<@" + reviewerId + ">");
    }
}
