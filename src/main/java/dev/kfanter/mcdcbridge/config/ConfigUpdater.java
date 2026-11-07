package dev.kfanter.mcdcbridge.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Brings config.yml and the lang files of an older install up to date:
 * <ol>
 *   <li>A timestamped backup goes to plugins/MCDCBridge/backups/ first. If that fails, nothing is touched.</li>
 *   <li>Keys that are new in this version are added; existing values are never overwritten.</li>
 *   <li>Config version 1 kept all texts in config.yml; those that were changed are moved into lang/de.yml
 *       (version 1 was German only) and removed from config.yml.</li>
 * </ol>
 */
public final class ConfigUpdater {

    /** Bump when the config layout changes in a way that needs migration. Keep in sync with config.yml. */
    public static final int CONFIG_VERSION = 2;
    private static final int MAX_BACKUPS = 10;
    private static final String VERSION_KEY = "config-version";
    private static final String LEGACY_LANG = "de";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final JavaPlugin plugin;
    private final String stamp = LocalDateTime.now().format(STAMP);

    public ConfigUpdater(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Call after the default files were saved and before the config is read. Never throws. */
    public void run() {
        try {
            updateConfig();
            updateLangFiles();
            plugin.reloadConfig();
        } catch (RuntimeException | IOException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Config update failed - files were left as they are (backups: plugins/MCDCBridge/backups)", e);
        }
    }

    private void updateConfig() throws IOException {
        File file = new File(plugin.getDataFolder(), "config.yml");
        YamlConfiguration shipped = shipped("config.yml");
        if (!file.isFile() || shipped == null) {
            return;
        }
        YamlConfiguration user = YamlConfiguration.loadConfiguration(file);
        int version = user.getInt(VERSION_KEY, 1);
        boolean legacy = version < 2;
        if (version >= CONFIG_VERSION && missingKeys(user, shipped).isEmpty()) {
            return;
        }
        backup(file, "config");
        if (legacy) {
            migrateLegacyTexts(user);
        }
        int added = addMissing(user, shipped);
        user.set(VERSION_KEY, CONFIG_VERSION);
        user.save(file);
        plugin.getLogger().info("Updated config.yml to version " + CONFIG_VERSION + " (" + added
                + " new key(s)). Backup: plugins/MCDCBridge/backups/config-" + stamp + ".yml");
    }

    /** Version 1 texts -> lang/de.yml (only values that differ from the German defaults), then drop them from the config. */
    private void migrateLegacyTexts(YamlConfiguration user) throws IOException {
        File langFile = new File(plugin.getDataFolder(), "lang/" + LEGACY_LANG + ".yml");
        YamlConfiguration shipped = shipped("lang/" + LEGACY_LANG + ".yml");
        if (shipped == null) {
            return;
        }
        YamlConfiguration lang = langFile.isFile() ? YamlConfiguration.loadConfiguration(langFile) : new YamlConfiguration();
        List<String> textPaths = new ArrayList<>();
        for (String path : shipped.getKeys(true)) {
            if (shipped.isString(path) && !path.startsWith("minecraft.")) {
                textPaths.add(path);
            }
        }
        // Optional embed footers existed in version 1 as well.
        if (user.isConfigurationSection("embeds")) {
            for (String path : user.getConfigurationSection("embeds").getKeys(true)) {
                if (path.endsWith(".footer")) {
                    textPaths.add("embeds." + path);
                }
            }
        }
        List<String[]> moves = new ArrayList<>();
        for (String path : textPaths) {
            moves.add(new String[] {path, path});
        }
        moves.add(new String[] {"moderation.ban-reason", "minecraft.ban-reason"});
        moves.add(new String[] {"moderation.tempban-reason", "minecraft.tempban-reason"});

        int migrated = 0;
        for (String[] move : moves) {
            String value = user.getString(move[0]);
            if (value != null && !value.equals(shipped.getString(move[1]))) {
                lang.set(move[1], value);
                migrated++;
            }
            user.set(move[0], null);
        }
        for (String section : List.of("modal", "modal-bedrock")) {
            user.set(section, null);
        }
        if (migrated > 0) {
            if (langFile.isFile()) {
                backup(langFile, "lang-" + LEGACY_LANG);
            }
            lang.save(langFile);
            plugin.getLogger().info("Moved " + migrated + " customised text(s) from config.yml to lang/"
                    + LEGACY_LANG + ".yml");
        }
    }

    private void updateLangFiles() throws IOException {
        File[] files = new File(plugin.getDataFolder(), "lang").listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return;
        }
        Arrays.sort(files);
        for (File file : files) {
            YamlConfiguration shipped = shipped("lang/" + file.getName());
            if (shipped == null) {
                continue; // a language the user added; nothing to compare with
            }
            YamlConfiguration user = YamlConfiguration.loadConfiguration(file);
            if (missingKeys(user, shipped).isEmpty()) {
                continue;
            }
            backup(file, "lang-" + file.getName().replaceFirst("\\.yml$", ""));
            int added = addMissing(user, shipped);
            user.save(file);
            plugin.getLogger().info("Added " + added + " new key(s) to lang/" + file.getName());
        }
    }

    private static List<String> missingKeys(YamlConfiguration user, YamlConfiguration shipped) {
        List<String> missing = new ArrayList<>();
        for (String path : shipped.getKeys(true)) {
            if (!shipped.isConfigurationSection(path) && !user.contains(path, true)) {
                missing.add(path);
            }
        }
        return missing;
    }

    private static int addMissing(YamlConfiguration user, YamlConfiguration shipped) {
        List<String> missing = missingKeys(user, shipped);
        for (String path : missing) {
            user.set(path, shipped.get(path));
            user.setComments(path, shipped.getComments(path));
        }
        return missing.size();
    }

    /** Copies the file to backups/&lt;prefix&gt;-&lt;timestamp&gt;.yml and keeps only the newest few per prefix. */
    private void backup(File file, String prefix) throws IOException {
        File dir = new File(plugin.getDataFolder(), "backups");
        Files.createDirectories(dir.toPath());
        Files.copy(file.toPath(), new File(dir, prefix + "-" + stamp + ".yml").toPath(),
                StandardCopyOption.REPLACE_EXISTING);
        File[] old = dir.listFiles((d, name) -> name.startsWith(prefix + "-") && name.endsWith(".yml")
                && name.length() == prefix.length() + 1 + stamp.length() + 4);
        if (old != null && old.length > MAX_BACKUPS) {
            Arrays.sort(old, Comparator.comparing(File::getName));
            for (int i = 0; i < old.length - MAX_BACKUPS; i++) {
                Files.deleteIfExists(old[i].toPath());
            }
        }
    }

    private YamlConfiguration shipped(String path) {
        try (InputStream in = plugin.getResource(path)) {
            return in == null ? null
                    : YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return null;
        }
    }
}
