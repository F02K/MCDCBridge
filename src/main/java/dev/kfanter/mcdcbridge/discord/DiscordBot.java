package dev.kfanter.mcdcbridge.discord;

import dev.kfanter.mcdcbridge.config.BridgeConfig;
import dev.kfanter.mcdcbridge.link.LinkStore;
import dev.kfanter.mcdcbridge.moderation.BanService;
import dev.kfanter.mcdcbridge.whitelist.WhitelistService;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;

/** Owns the JDA connection and wires up the interaction listeners. */
public final class DiscordBot {

    private final BridgeConfig config;
    private final WhitelistListener whitelistListener;
    private final ModerationListener moderationListener;
    private final JDA jda;

    public DiscordBot(BridgeConfig config, WhitelistService whitelist, BanService bans, LinkStore links) {
        this.config = config;
        this.whitelistListener = new WhitelistListener(config, new EmbedFactory(config), whitelist, links);
        this.moderationListener = new ModerationListener(config, links, bans);
        // Role changes are only reported for cached members, and need the privileged GUILD_MEMBERS intent.
        this.jda = JDABuilder.createLight(config.token())
                .enableIntents(GatewayIntent.GUILD_MEMBERS)
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .setChunkingFilter(ChunkingFilter.ALL)
                .addEventListeners(whitelistListener, moderationListener)
                .build();
    }

    /** Posts the whitelist request panel into the configured channel. */
    public void postWhitelistPanel() {
        String channelId = config.panelChannelId();
        TextChannel channel = channelId.isEmpty() ? null : jda.getTextChannelById(channelId);
        if (channel == null) {
            throw new IllegalStateException(
                    "whitelist.panel-channel-id is not set or the channel is not visible to the bot");
        }
        channel.sendMessage(whitelistListener.panel()).queue();
    }

    public void sweepExpiredTempbans() {
        moderationListener.sweepExpiredTempbans(jda);
    }

    public void shutdown() {
        jda.shutdown();
    }
}
