package dev.kfanter.mcdcbridge.lang;

import dev.kfanter.mcdcbridge.config.BridgeConfig;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.regex.Pattern;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Language bundles (plugins/MCDCBridge/lang/&lt;code&gt;.yml) and the choice which one is used where.
 * <p>
 * Minecraft side ({@code language.minecraft}): a fixed code, or "discord" to follow the Discord server language.
 * Discord side ({@code language.discord}): "auto" = Discord server language, "minecraft" = same as the
 * Minecraft side, or a fixed code. Unknown languages fall back to English; missing keys too.
 */
public final class Messages {

    private static final String DEFAULT = "en";
    private static final List<String> BUNDLED = List.of("en", "de");
    private static final Pattern SAFE_CODE = Pattern.compile("[a-z]{2,3}(_[a-z0-9]{2,4})*");

    private final JavaPlugin plugin;
    private final BridgeConfig config;
    private final Map<String, Optional<YamlConfiguration>> cache = new ConcurrentHashMap<>();
    private volatile Supplier<String> guildLocale = () -> null;

    public Messages(JavaPlugin plugin, BridgeConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    /** Copies the shipped language files into the data folder (never overwrites edited ones). */
    public void install() {
        for (String code : BUNDLED) {
            if (!new File(plugin.getDataFolder(), "lang/" + code + ".yml").exists()) {
                plugin.saveResource("lang/" + code + ".yml", false);
            }
        }
    }

    /** Where "discord" / "auto" get the Discord server language from (a tag like "de" or "en-US", or null). */
    public void setGuildLocaleSource(Supplier<String> source) {
        this.guildLocale = source;
    }

    /** Forget loaded bundles so edited files are read again. */
    public void reload() {
        cache.clear();
    }

    /** Language for Discord output in a guild with the given preferred locale. */
    public Lang discord(String guildTag) {
        String setting = config.discordLanguage();
        String tag = switch (setting) {
            case "auto" -> guildTag;
            case "minecraft" -> minecraftTag(guildTag);
            default -> setting;
        };
        return lang(tag);
    }

    /** Language for Minecraft output (commands, kick messages). */
    public Lang minecraft() {
        return lang(minecraftTag(guildLocale.get()));
    }

    private String minecraftTag(String guildTag) {
        String setting = config.minecraftLanguage();
        return setting.equals("discord") ? guildTag : setting;
    }

    private Lang lang(String tag) {
        for (String candidate : candidates(tag)) {
            YamlConfiguration bundle = bundle(candidate);
            if (bundle == null) {
                continue;
            }
            YamlConfiguration english = bundle(DEFAULT);
            List<FileConfiguration> chain = new ArrayList<>(List.of(bundle));
            if (english != null && !candidate.equals(DEFAULT)) {
                chain.add(english);
            }
            return new Lang(candidate, chain);
        }
        throw new IllegalStateException("Missing language bundle lang/" + DEFAULT + ".yml");
    }

    /** "pt-BR" -> [pt_br, pt, en]. Always ends with the default language. */
    private static List<String> candidates(String tag) {
        List<String> result = new ArrayList<>();
        if (tag != null) {
            String normalized = tag.trim().toLowerCase(Locale.ROOT).replace('-', '_');
            if (SAFE_CODE.matcher(normalized).matches()) {
                result.add(normalized);
                int underscore = normalized.indexOf('_');
                if (underscore > 0) {
                    result.add(normalized.substring(0, underscore));
                }
            }
        }
        if (!result.contains(DEFAULT)) {
            result.add(DEFAULT);
        }
        return result;
    }

    /** The data-folder file, with the shipped file of the same name as defaults; null if neither exists. */
    private YamlConfiguration bundle(String code) {
        return cache.computeIfAbsent(code, c -> Optional.ofNullable(read(c))).orElse(null);
    }

    private YamlConfiguration read(String code) {
        String path = "lang/" + code + ".yml";
        YamlConfiguration shipped = null;
        try (InputStream in = plugin.getResource(path)) {
            if (in != null) {
                shipped = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not read bundled " + path, e);
        }
        File file = new File(plugin.getDataFolder(), path);
        if (!file.isFile()) {
            return shipped;
        }
        YamlConfiguration custom = YamlConfiguration.loadConfiguration(file);
        if (shipped != null) {
            custom.setDefaults(shipped);
        }
        return custom;
    }
}
