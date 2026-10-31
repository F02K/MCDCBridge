package dev.kfanter.mcdcbridge.command;

import dev.kfanter.mcdcbridge.config.BridgeConfig;
import dev.kfanter.mcdcbridge.discord.DiscordBot;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

/** /dcbridge panel | reload */
public final class BridgeCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of("panel", "reload");

    private final BridgeConfig config;
    private final DiscordBot bot;

    public BridgeCommand(BridgeConfig config, DiscordBot bot) {
        this.config = config;
        this.bot = bot;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            return false;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "panel" -> syncPanel(sender, "Whitelist panel updated.");
            case "reload" -> {
                config.reload();
                syncPanel(sender, "Config reloaded, panel updated (token changes need a restart).");
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private void syncPanel(CommandSender sender, String success) {
        try {
            bot.syncWhitelistPanel();
            sender.sendMessage(Component.text(success));
        } catch (IllegalStateException e) {
            sender.sendMessage(Component.text(e.getMessage()));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1 ? SUBCOMMANDS : List.of();
    }
}
