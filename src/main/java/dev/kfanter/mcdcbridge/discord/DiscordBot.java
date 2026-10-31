package dev.kfanter.mcdcbridge.discord;

import dev.kfanter.mcdcbridge.config.BridgeConfig;
import dev.kfanter.mcdcbridge.link.LinkStore;
import dev.kfanter.mcdcbridge.moderation.BanService;
import dev.kfanter.mcdcbridge.whitelist.WhitelistService;
import java.util.logging.Logger;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

/** Owns the JDA connection and wires up the interaction listeners. */
public final class DiscordBot {

    private final BridgeConfig config;
    private final LinkStore links;
    private final Logger logger;
    private final WhitelistListener whitelistListener;
    private final ModerationListener moderationListener;
    private final JDA jda;

    public DiscordBot(BridgeConfig config, WhitelistService whitelist, BanService bans, LinkStore links, Logger logger) {
        this.config = config;
        this.links = links;
        this.logger = logger;
        this.whitelistListener = new WhitelistListener(config, new EmbedFactory(config), whitelist, links);
        this.moderationListener = new ModerationListener(config, links, bans);
        // Role changes are only reported for cached members, and need the privileged GUILD_MEMBERS intent.
        this.jda = JDABuilder.createLight(config.token())
                .enableIntents(GatewayIntent.GUILD_MEMBERS)
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .setChunkingFilter(ChunkingFilter.ALL)
                .addEventListeners(whitelistListener, moderationListener, new ListenerAdapter() {
                    @Override
                    public void onReady(ReadyEvent event) {
                        try {
                            syncWhitelistPanel();
                        } catch (IllegalStateException e) {
                            logger.warning(e.getMessage());
                        }
                    }
                })
                .build();
    }

    /**
     * Makes the panel channel show the current panel: edits the bot's existing panel message in place,
     * or posts a new one if there is none (yet) or it was deleted.
     */
    public void syncWhitelistPanel() {
        String channelId = config.panelChannelId();
        TextChannel channel = channelId.isEmpty() ? null : jda.getTextChannelById(channelId);
        if (channel == null) {
            throw new IllegalStateException(
                    "whitelist.panel-channel-id is not set or the channel is not visible to the bot");
        }
        String messageId = links.panelMessageId();
        if (messageId.isEmpty()) {
            postPanel(channel);
            return;
        }
        channel.editMessageById(messageId, MessageEditData.fromCreateData(whitelistListener.panel()))
                .queue(edited -> { }, failure -> postPanel(channel));
    }

    private void postPanel(TextChannel channel) {
        channel.sendMessage(whitelistListener.panel()).queue(
                message -> links.setPanelMessageId(message.getId()),
                failure -> logger.warning("Could not post the whitelist panel: " + failure.getMessage()));
    }

    public void sweepExpiredTempbans() {
        moderationListener.sweepExpiredTempbans(jda);
    }

    public void shutdown() {
        jda.shutdown();
    }
}
