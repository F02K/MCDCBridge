package dev.kfanter.mcdcbridge.whitelist;

import com.destroystokyo.paper.profile.PlayerProfile;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

/** Adds players to the server whitelist without blocking the main thread. */
public final class WhitelistService {

    public enum Result { ADDED, ALREADY_WHITELISTED, NOT_FOUND }

    /** @param uuid null when the result is {@link Result#NOT_FOUND} */
    public record Outcome(Result result, UUID uuid) {}

    private final Plugin plugin;

    public WhitelistService(Plugin plugin) {
        this.plugin = plugin;
    }

    /** The returned future completes on the main thread. */
    public CompletableFuture<Outcome> add(String name) {
        CompletableFuture<Outcome> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            UUID id = resolve(name);
            Bukkit.getScheduler().runTask(plugin, () -> future.complete(whitelist(id)));
        });
        return future;
    }

    private UUID resolve(String name) {
        if (!Bukkit.getOnlineMode()) {
            return Bukkit.getOfflinePlayer(name).getUniqueId();
        }
        try {
            PlayerProfile profile = Bukkit.createProfile(name);
            return profile.complete(false) ? profile.getId() : null;
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Could not look up " + name, e);
            return null;
        }
    }

    private Outcome whitelist(UUID id) {
        if (id == null) {
            return new Outcome(Result.NOT_FOUND, null);
        }
        OfflinePlayer player = Bukkit.getOfflinePlayer(id);
        if (player.isWhitelisted()) {
            return new Outcome(Result.ALREADY_WHITELISTED, id);
        }
        player.setWhitelisted(true);
        return new Outcome(Result.ADDED, id);
    }
}
