package dev.kfanter.mcdcbridge.config;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

/** Typed, reload-friendly view on config.yml. Values are read on every call. */
public final class BridgeConfig {

    private static final Pattern DURATION = Pattern.compile("(\\d+)([mhd])");

    private final JavaPlugin plugin;

    public BridgeConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
    }

    public String token() {
        return plugin.getConfig().getString("discord.token", "").trim();
    }

    public String panelChannelId() {
        return plugin.getConfig().getString("whitelist.panel-channel-id", "").trim();
    }

    public String reviewChannelId() {
        return plugin.getConfig().getString("whitelist.review-channel-id", "").trim();
    }

    public String staffRoleId() {
        return plugin.getConfig().getString("whitelist.staff-role-id", "").trim();
    }

    /** Role given to a Discord user once they are whitelisted. */
    public String memberRoleId() {
        return plugin.getConfig().getString("whitelist.member-role-id", "").trim();
    }

    public String banRoleId() {
        return plugin.getConfig().getString("moderation.ban-role-id", "").trim();
    }

    public String tempbanRoleId() {
        return plugin.getConfig().getString("moderation.tempban-role-id", "").trim();
    }

    public String banReason() {
        return plugin.getConfig().getString("moderation.ban-reason", "Banned");
    }

    public String tempbanReason() {
        return plugin.getConfig().getString("moderation.tempban-reason", "Temporarily banned");
    }

    /** Parses values like "30m", "12h" or "7d". */
    public Duration tempbanDuration() {
        String value = plugin.getConfig().getString("moderation.tempban-duration", "7d").trim();
        Matcher matcher = DURATION.matcher(value);
        if (!matcher.matches()) {
            throw new IllegalStateException("moderation.tempban-duration must look like 30m, 12h or 7d");
        }
        long amount = Long.parseLong(matcher.group(1));
        return switch (matcher.group(2)) {
            case "m" -> Duration.ofMinutes(amount);
            case "h" -> Duration.ofHours(amount);
            default -> Duration.ofDays(amount);
        };
    }

    public boolean autoApprove() {
        return plugin.getConfig().getBoolean("whitelist.auto-approve", false);
    }

    public ConfigurationSection section(String path) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(path);
        if (section == null) {
            throw new IllegalStateException("Missing config section '" + path + "'");
        }
        return section;
    }
}
