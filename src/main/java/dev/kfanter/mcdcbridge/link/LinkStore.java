package dev.kfanter.mcdcbridge.link;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Persists which Minecraft account belongs to which Discord user, plus running tempban expiries. */
public final class LinkStore {

    public record Link(UUID uuid, String name, String guildId) {}

    private final File file;
    private final YamlConfiguration yaml;

    public LinkStore(File dataFolder) {
        this.file = new File(dataFolder, "links.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    public synchronized void link(String discordId, UUID uuid, String name, String guildId) {
        yaml.set("links." + discordId + ".uuid", uuid.toString());
        yaml.set("links." + discordId + ".name", name);
        yaml.set("links." + discordId + ".guild", guildId);
        save();
    }

    public synchronized Optional<Link> find(String discordId) {
        ConfigurationSection section = yaml.getConfigurationSection("links." + discordId);
        if (section == null) {
            return Optional.empty();
        }
        return Optional.of(new Link(
                UUID.fromString(section.getString("uuid", "")),
                section.getString("name", ""),
                section.getString("guild", "")));
    }

    public synchronized void setTempban(String discordId, long untilMillis) {
        yaml.set("tempbans." + discordId, untilMillis);
        save();
    }

    public synchronized void clearTempban(String discordId) {
        yaml.set("tempbans." + discordId, null);
        save();
    }

    /** Discord ids whose tempban has run out by {@code nowMillis}. */
    public synchronized List<String> expiredTempbans(long nowMillis) {
        ConfigurationSection section = yaml.getConfigurationSection("tempbans");
        if (section == null) {
            return List.of();
        }
        return section.getKeys(false).stream()
                .filter(id -> section.getLong(id) <= nowMillis)
                .toList();
    }

    /** Id of the bot's whitelist panel message, or an empty string if none was posted yet. */
    public synchronized String panelMessageId() {
        return yaml.getString("panel-message", "");
    }

    public synchronized void setPanelMessageId(String messageId) {
        yaml.set("panel-message", messageId);
        save();
    }

    private void save() {
        try {
            yaml.save(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save " + file, e);
        }
    }
}
