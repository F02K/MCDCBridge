package dev.kfanter.mcdcbridge.discord;

import dev.kfanter.mcdcbridge.config.BridgeConfig;
import dev.kfanter.mcdcbridge.lang.Messages;
import dev.kfanter.mcdcbridge.link.LinkStore;
import dev.kfanter.mcdcbridge.moderation.BanService;
import java.time.Instant;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * Mirrors the Discord ban / tempban roles onto the Minecraft ban list:
 * role added -> ban, role removed -> pardon. Whitelist and member role stay untouched.
 */
public final class ModerationListener extends ListenerAdapter {

    private final BridgeConfig config;
    private final LinkStore links;
    private final BanService bans;
    private final Messages messages;

    public ModerationListener(BridgeConfig config, Messages messages, LinkStore links, BanService bans) {
        this.config = config;
        this.messages = messages;
        this.links = links;
        this.bans = bans;
    }

    @Override
    public void onGuildMemberRoleAdd(GuildMemberRoleAddEvent event) {
        String userId = event.getUser().getId();
        for (Role role : event.getRoles()) {
            if (role.getId().equals(config.banRoleId())) {
                links.find(userId).ifPresent(link -> bans.ban(link.uuid(), null, messages.minecraft().get("minecraft.ban-reason")));
            } else if (role.getId().equals(config.tempbanRoleId())) {
                Instant until = Instant.now().plus(config.tempbanDuration());
                links.find(userId).ifPresent(link -> {
                    links.setTempban(userId, until.toEpochMilli());
                    bans.ban(link.uuid(), until, messages.minecraft().get("minecraft.tempban-reason"));
                });
            }
        }
    }

    @Override
    public void onGuildMemberRoleRemove(GuildMemberRoleRemoveEvent event) {
        String userId = event.getUser().getId();
        for (Role role : event.getRoles()) {
            String id = role.getId();
            if (id.equals(config.banRoleId()) || id.equals(config.tempbanRoleId())) {
                links.clearTempban(userId);
                links.find(userId).ifPresent(link -> bans.pardon(link.uuid()));
            }
        }
    }

    /** Takes the tempban role from everyone whose tempban ran out; the role removal event does the pardon. */
    public void sweepExpiredTempbans(JDA jda) {
        for (String userId : links.expiredTempbans(System.currentTimeMillis())) {
            links.clearTempban(userId);
            links.find(userId).ifPresent(link -> {
                Guild guild = jda.getGuildById(link.guildId());
                Role role = guild == null || config.tempbanRoleId().isEmpty()
                        ? null : guild.getRoleById(config.tempbanRoleId());
                if (role != null) {
                    guild.removeRoleFromMember(UserSnowflake.fromId(userId), role).queue();
                } else {
                    bans.pardon(link.uuid());
                }
            });
        }
    }
}
