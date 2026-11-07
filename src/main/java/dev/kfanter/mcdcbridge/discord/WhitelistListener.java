package dev.kfanter.mcdcbridge.discord;

import dev.kfanter.mcdcbridge.config.BridgeConfig;
import dev.kfanter.mcdcbridge.lang.Lang;
import dev.kfanter.mcdcbridge.lang.Messages;
import dev.kfanter.mcdcbridge.link.LinkStore;
import dev.kfanter.mcdcbridge.whitelist.WhitelistService;
import dev.kfanter.mcdcbridge.whitelist.WhitelistService.Outcome;
import dev.kfanter.mcdcbridge.whitelist.WhitelistService.Result;
import java.util.ArrayList;
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
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

/**
 * The whole Discord side of the whitelist flow:
 * panel button -> modal -> review embed (approve/deny buttons) -> whitelist.
 * All state lives in the component ids, so pending requests survive restarts.
 */
public final class WhitelistListener extends ListenerAdapter {

    private static final String REQUEST = "whitelist:request";
    private static final String REQUEST_BEDROCK = "whitelist:request-bedrock";
    private static final String MODAL = "whitelist:modal";
    private static final String MODAL_BEDROCK = "whitelist:modal-bedrock";
    private static final String APPROVE = "whitelist:approve:";
    private static final String DENY = "whitelist:deny:";
    private static final String NAME_INPUT = "player";
    private static final Pattern VALID_NAME = Pattern.compile("\\w{3,16}");
    private static final Pattern VALID_GAMERTAG = Pattern.compile("[A-Za-z0-9]+( [A-Za-z0-9]+)*");

    private final BridgeConfig config;
    private final EmbedFactory embeds;
    private final WhitelistService whitelist;
    private final LinkStore links;
    private final Messages messages;

    public WhitelistListener(BridgeConfig config, Messages messages, EmbedFactory embeds, WhitelistService whitelist,
                             LinkStore links) {
        this.config = config;
        this.messages = messages;
        this.embeds = embeds;
        this.whitelist = whitelist;
        this.links = links;
    }

    /** Language for output in a guild: its Discord server language unless config says otherwise. */
    private Lang lang(Guild guild) {
        return messages.discord(guild == null ? null : guild.getLocale().getLocale());
    }

    /** The message with the "request whitelist" button, in the language of the given guild. */
    public MessageCreateData panel(Guild guild) {
        Lang lang = lang(guild);
        List<Button> buttons = new ArrayList<>();
        buttons.add(embeds.button(lang, "request", REQUEST));
        if (whitelist.bedrockAvailable()) {
            buttons.add(embeds.button(lang, "request-bedrock", REQUEST_BEDROCK));
        }
        return new MessageCreateBuilder()
                .setEmbeds(embeds.embed(lang, "panel", Map.of()))
                .addActionRow(buttons)
                .build();
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        Lang lang = lang(event.getGuild());
        if (id.equals(REQUEST)) {
            event.replyModal(modal(lang, false)).queue();
        } else if (id.equals(REQUEST_BEDROCK)) {
            if (whitelist.bedrockAvailable()) {
                event.replyModal(modal(lang, true)).queue();
            } else {
                event.replyEmbeds(embeds.embed(lang, "error", Map.of())).setEphemeral(true).queue();
            }
        } else if (id.startsWith(APPROVE)) {
            review(event, true);
        } else if (id.startsWith(DENY)) {
            review(event, false);
        }
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        boolean bedrock = event.getModalId().equals(MODAL_BEDROCK);
        if (!bedrock && !event.getModalId().equals(MODAL)) {
            return;
        }
        Lang lang = lang(event.getGuild());
        String input = event.getValue(NAME_INPUT).getAsString().trim();
        String userId = event.getUser().getId();
        if (bedrock) {
            if (!whitelist.bedrockAvailable()) {
                event.replyEmbeds(embeds.embed(lang, "error", Map.of())).setEphemeral(true).queue();
                return;
            }
            if (!VALID_GAMERTAG.matcher(input).matches()) {
                event.replyEmbeds(embeds.embed(lang, "invalid-gamertag", placeholders(input, userId, null)))
                        .setEphemeral(true).queue();
                return;
            }
        }
        String player = bedrock ? whitelist.bedrockName(input) : input;
        Map<String, String> placeholders = placeholders(player, userId, null);
        if (!bedrock && !VALID_NAME.matcher(player).matches()) {
            event.replyEmbeds(embeds.embed(lang, "invalid-name", placeholders)).setEphemeral(true).queue();
            return;
        }
        if (hasRole(event.getMember(), config.banRoleId()) || hasRole(event.getMember(), config.tempbanRoleId())) {
            event.replyEmbeds(embeds.embed(lang, "banned", placeholders)).setEphemeral(true).queue();
            return;
        }
        event.deferReply(true).queue();
        if (config.autoApprove()) {
            whitelist.add(player).thenAccept(outcome -> {
                onWhitelisted(event.getGuild(), event.getUser().getId(), player, outcome);
                edit(event.getHook(), lang, resultEmbed(outcome.result()), placeholders);
            });
        } else {
            sendForReview(event.getHook(), lang, event.getJDA().getTextChannelById(config.reviewChannelId()),
                    player, event.getUser().getId(), placeholders);
        }
    }

    private void sendForReview(InteractionHook hook, Lang lang, TextChannel channel, String player, String userId,
                               Map<String, String> placeholders) {
        if (channel == null) {
            edit(hook, lang, "error", placeholders);
            return;
        }
        String payload = userId + ":" + player;
        channel.sendMessageEmbeds(embeds.embed(lang, "review", placeholders))
                .addActionRow(embeds.button(lang, "approve", APPROVE + payload), embeds.button(lang, "deny", DENY + payload))
                .queue(sent -> edit(hook, lang, "request-sent", placeholders),
                        failure -> edit(hook, lang, "error", placeholders));
    }

    private void review(ButtonInteractionEvent event, boolean approve) {
        Lang lang = lang(event.getGuild());
        // Component id: whitelist:<approve|deny>:<requesterId>:<player>
        String[] parts = event.getComponentId().split(":", 4);
        Map<String, String> placeholders = placeholders(parts[3], parts[2], event.getUser().getId());
        if (!isStaff(event.getMember())) {
            event.replyEmbeds(embeds.embed(lang, "no-permission", placeholders)).setEphemeral(true).queue();
            return;
        }
        if (!approve) {
            event.editMessageEmbeds(embeds.embed(lang, "denied", placeholders)).setComponents(List.of()).queue();
            return;
        }
        event.deferEdit().queue();
        whitelist.add(parts[3]).thenAccept(outcome -> {
            onWhitelisted(event.getGuild(), parts[2], parts[3], outcome);
            event.getHook().editOriginalEmbeds(embeds.embed(lang, resultEmbed(outcome.result()), placeholders))
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

    private Modal modal(Lang lang, boolean bedrock) {
        String section = bedrock ? "modal-bedrock" : "modal";
        TextInput input = TextInput.create(NAME_INPUT, lang.get(section + ".label"), TextInputStyle.SHORT)
                .setPlaceholder(lang.getOrNull(section + ".placeholder"))
                .setMinLength(bedrock ? 1 : 3)
                .setMaxLength(bedrock ? 15 : 16)
                .setRequired(true)
                .build();
        return Modal.create(bedrock ? MODAL_BEDROCK : MODAL, lang.get(section + ".title"))
                .addComponents(ActionRow.of(input))
                .build();
    }

    private void edit(InteractionHook hook, Lang lang, String embedKey, Map<String, String> placeholders) {
        hook.editOriginalEmbeds(embeds.embed(lang, embedKey, placeholders)).queue();
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
