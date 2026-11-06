package dev.kfanter.mcdcbridge.whitelist;

import java.util.UUID;
import org.geysermc.floodgate.api.FloodgateApi;

/** The only class that touches the Floodgate API, so the plugin still loads without Floodgate. */
final class FloodgateBridge {

    private FloodgateBridge() {}

    static String prefix() {
        return FloodgateApi.getInstance().getPlayerPrefix();
    }

    /** Resolves the Floodgate UUID of an Xbox gamertag (blocking lookup, call off the main thread). */
    static UUID uuidFor(String gamertag) throws Exception {
        return FloodgateApi.getInstance().getUuidFor(gamertag).get();
    }
}
