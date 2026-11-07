package dev.kfanter.mcdcbridge;

import dev.kfanter.mcdcbridge.command.BridgeCommand;
import dev.kfanter.mcdcbridge.config.BridgeConfig;
import dev.kfanter.mcdcbridge.config.ConfigUpdater;
import dev.kfanter.mcdcbridge.discord.DiscordBot;
import dev.kfanter.mcdcbridge.lang.Messages;
import dev.kfanter.mcdcbridge.link.LinkStore;
import dev.kfanter.mcdcbridge.moderation.BanService;
import dev.kfanter.mcdcbridge.whitelist.WhitelistService;
import java.util.Objects;
import java.util.logging.Level;
import org.bukkit.plugin.java.JavaPlugin;

public final class DiscordBridgePlugin extends JavaPlugin {

    private DiscordBot bot;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        BridgeConfig config = new BridgeConfig(this);
        Messages messages = new Messages(this, config);
        messages.install();
        new ConfigUpdater(this).run();
        if (config.token().isEmpty()) {
            getLogger().severe("discord.token is empty - set it in config.yml and restart.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        try {
            bot = new DiscordBot(config, messages, new WhitelistService(this), new BanService(this), new LinkStore(getDataFolder()), getLogger());
        } catch (RuntimeException e) {
            getLogger().log(Level.SEVERE, "Could not connect to Discord", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getServer().getScheduler().runTaskTimerAsynchronously(this, bot::sweepExpiredTempbans, 200L, 1200L);
        Objects.requireNonNull(getCommand("dcbridge")).setExecutor(new BridgeCommand(config, messages, bot));
    }

    @Override
    public void onDisable() {
        if (bot != null) {
            bot.shutdown();
        }
    }
}
