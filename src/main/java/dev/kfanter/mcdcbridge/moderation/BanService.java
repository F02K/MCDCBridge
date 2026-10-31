package dev.kfanter.mcdcbridge.moderation;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.plugin.Plugin;

/** Bans and pardons players on the main thread. The whitelist is never touched. */
public final class BanService {

    private static final String SOURCE = "MCDCBridge";

    private final Plugin plugin;

    public BanService(Plugin plugin) {
        this.plugin = plugin;
    }

    /** @param expires null for a permanent ban */
    public void ban(UUID id, Instant expires, String reason) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            OfflinePlayer player = Bukkit.getOfflinePlayer(id);
            player.ban(reason, expires == null ? null : Date.from(expires), SOURCE);
            Player online = player.getPlayer();
            if (online != null) {
                online.kick(Component.text(reason));
            }
        });
    }

    public void pardon(UUID id) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            BanList<PlayerProfile> banList = Bukkit.getBanList(BanList.Type.PROFILE);
            banList.pardon(Bukkit.getOfflinePlayer(id).getPlayerProfile());
        });
    }
}
