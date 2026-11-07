package dev.kfanter.mcdcbridge.discord;

import dev.kfanter.mcdcbridge.config.BridgeConfig;
import dev.kfanter.mcdcbridge.lang.Lang;
import java.awt.Color;
import java.util.Locale;
import java.util.Map;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;

/** Builds embeds and buttons: texts from the language bundle, colors and styles from config.yml. */
public final class EmbedFactory {

    private final BridgeConfig config;

    public EmbedFactory(BridgeConfig config) {
        this.config = config;
    }

    public MessageEmbed embed(Lang lang, String key, Map<String, String> placeholders) {
        EmbedBuilder builder = new EmbedBuilder()
                .setTitle(lang.get("embeds." + key + ".title", placeholders))
                .setDescription(lang.get("embeds." + key + ".description", placeholders))
                .setColor(Color.decode(config.embedColor(key)));
        String footer = lang.getOrNull("embeds." + key + ".footer");
        if (footer != null) {
            builder.setFooter(Lang.fill(footer, placeholders));
        }
        return builder.build();
    }

    public Button button(Lang lang, String key, String componentId) {
        ButtonStyle style = ButtonStyle.valueOf(config.buttonStyle(key).toUpperCase(Locale.ROOT));
        String label = lang.get("buttons." + key + ".label");
        String emoji = config.buttonEmoji(key);
        return emoji == null
                ? Button.of(style, componentId, label)
                : Button.of(style, componentId, label, Emoji.fromUnicode(emoji));
    }
}
