package dev.kfanter.mcdcbridge.lang;

import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.FileConfiguration;

/** One resolved language: the chosen bundle first, English as fallback for missing keys. */
public final class Lang {

    private final String code;
    private final List<FileConfiguration> chain;

    Lang(String code, List<FileConfiguration> chain) {
        this.code = code;
        this.chain = chain;
    }

    /** Bundle code that was actually picked, e.g. "de". */
    public String code() {
        return code;
    }

    /** The text for a key, or null if no bundle has it. */
    public String getOrNull(String path) {
        for (FileConfiguration bundle : chain) {
            String value = bundle.getString(path);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /** The text for a key; the key itself if it is missing everywhere, so gaps are visible. */
    public String get(String path) {
        String value = getOrNull(path);
        return value == null ? path : value;
    }

    /** Like {@link #get(String)} with {name} placeholders replaced. */
    public String get(String path, Map<String, String> placeholders) {
        return fill(get(path), placeholders);
    }

    public static String fill(String text, Map<String, String> placeholders) {
        if (text == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            text = text.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return text;
    }
}
