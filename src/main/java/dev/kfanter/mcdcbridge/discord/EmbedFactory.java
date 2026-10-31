package dev.kfanter.mcdcbridge.discord;

import dev.kfanter.mcdcbridge.config.BridgeConfig;
import java.awt.Color;
import java.util.Locale;
import java.util.Map;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import org.bukkit.configuration.ConfigurationSection;

/** Builds embeds and buttons from the `embeds` and `buttons` sections of config.yml. */
public final class EmbedFactory {

    private final BridgeConfig config;

    public EmbedFactory(BridgeConfig config) {
        this.config = config;
    }

    public MessageEmbed embed(String key, Map<String, String> placeholders) {
        ConfigurationSection section = config.section("embeds." + key);
        EmbedBuilder builder = new EmbedBuilder()
                .setTitle(fill(section.getString("title"), placeholders))
                .setDescription(fill(section.getString("description"), placeholders))
                .setColor(Color.decode(section.getString("color", "#5865F2")));
        String footer = section.getString("footer");
        if (footer != null) {
            builder.setFooter(fill(footer, placeholders));
        }
        return builder.build();
    }

    public Button button(String key, String componentId) {
        ConfigurationSection section = config.section("buttons." + key);
        ButtonStyle style = ButtonStyle.valueOf(section.getString("style", "PRIMARY").toUpperCase(Locale.ROOT));
        String label = section.getString("label", key);
        String emoji = section.getString("emoji");
        return emoji == null
                ? Button.of(style, componentId, label)
                : Button.of(style, componentId, label, Emoji.fromUnicode(emoji));
    }

    private static String fill(String text, Map<String, String> placeholders) {
        if (text == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            text = text.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return text;
    }
}
