package dev.kfanter.mcdcbridge.config;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    /** "en", "de", ... or "discord" (follow the Discord server language). */
    public String minecraftLanguage() {
        return plugin.getConfig().getString("language.minecraft", "en").trim().toLowerCase(Locale.ROOT);
    }

    /** "auto" (Discord server language), "minecraft" or a fixed language code. */
    public String discordLanguage() {
        return plugin.getConfig().getString("language.discord", "auto").trim().toLowerCase(Locale.ROOT);
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

    public String embedColor(String key) {
        return plugin.getConfig().getString("embeds." + key + ".color", "#5865F2");
    }

    public String buttonStyle(String key) {
        return plugin.getConfig().getString("buttons." + key + ".style", "PRIMARY");
    }

    /** Null when the button has no emoji. */
    public String buttonEmoji(String key) {
        String emoji = plugin.getConfig().getString("buttons." + key + ".emoji", "");
        return emoji.isBlank() ? null : emoji;
    }
}
