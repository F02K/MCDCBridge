package dev.kfanter.mcdcbridge.command;

import dev.kfanter.mcdcbridge.config.BridgeConfig;
import dev.kfanter.mcdcbridge.discord.DiscordBot;
import dev.kfanter.mcdcbridge.lang.Lang;
import dev.kfanter.mcdcbridge.lang.Messages;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.dv8tion.jda.api.entities.Guild;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

/** /dcbridge panel | reload | language */
public final class BridgeCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of("panel", "reload", "language");

    private final BridgeConfig config;
    private final Messages messages;
    private final DiscordBot bot;

    public BridgeCommand(BridgeConfig config, Messages messages, DiscordBot bot) {
        this.config = config;
        this.messages = messages;
        this.bot = bot;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            return false;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "panel" -> syncPanel(sender, "minecraft.panel-updated");
            case "reload" -> {
                config.reload();
                messages.reload();
                syncPanel(sender, "minecraft.config-reloaded");
            }
            case "language" -> showLanguage(sender);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void syncPanel(CommandSender sender, String successKey) {
        try {
            bot.syncWhitelistPanel();
            sender.sendMessage(Component.text(messages.minecraft().get(successKey)));
        } catch (IllegalStateException e) {
            sender.sendMessage(Component.text(e.getMessage()));
        }
    }

    /** Shows what Discord reports as the server language and which languages that results in. */
    private void showLanguage(CommandSender sender) {
        Lang minecraft = messages.minecraft();
        Guild guild = bot.primaryGuild();
        String unknown = minecraft.get("minecraft.language-unknown");
        String guildText = guild == null
                ? unknown
                : guild.getName() + " (" + guild.getLocale().getLocale() + ")";
        String discord = messages.discord(guild == null ? null : guild.getLocale().getLocale()).code();
        sender.sendMessage(Component.text(minecraft.get("minecraft.language-info", Map.of(
                "guild", guildText,
                "minecraft", minecraft.code(),
                "discord", discord))));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1 ? SUBCOMMANDS : List.of();
    }
}
