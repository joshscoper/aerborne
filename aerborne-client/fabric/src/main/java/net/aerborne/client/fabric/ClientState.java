package net.aerborne.client.fabric;

import java.nio.file.Path;
import java.util.UUID;
import net.aerborne.client.protocol.ClientPreferences;
import net.aerborne.client.protocol.ClientSession;
import net.aerborne.client.protocol.ClientAccess;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.slf4j.LoggerFactory;

public final class ClientState {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("aerborne-client.properties");
    private static boolean readFailed;
    public static String saveError = "";
    public static ClientPreferences preferences = load();
    public static final ClientSession SESSION = new ClientSession();
    public static UUID world;
    /** The server's latest stats for this player, or null when the server sends none (see HudPayload). */
    public static HudPayload hud;
    public static long hudAt;
    /** Creatures the server wants labelled (PlatesPayload), or null. */
    public static PlatesPayload plates;
    private ClientState() { }
    private static ClientPreferences load() {
        try { return ClientPreferences.load(FILE); }
        catch (Exception e) {
            LoggerFactory.getLogger("Aerborne").error("Could not load client preferences", e);
            readFailed = true;
            saveError = "Preferences unreadable; original file preserved.";
            // Keep the original file available for recovery and do not seed servers after a failed read.
            ClientPreferences p = new ClientPreferences(); p.serverSeeded = true; return p;
        }
    }
    public static void save() {
        if (readFailed) return;
        try { preferences.save(FILE); saveError = ""; }
        catch (Exception e) { saveError = "Could not save. Check file permissions."; LoggerFactory.getLogger("Aerborne").error(saveError, e); }
    }
    public static boolean bridgeAvailable() { return SESSION.menuAvailable(System.nanoTime()); }
    public static boolean available() {
        Minecraft mc = Minecraft.getInstance();
        return ClientAccess.localToolsAvailable(mc.player != null && mc.getConnection() != null,
                mc.getCurrentServer() == null ? null : mc.getCurrentServer().ip, bridgeAvailable());
    }
    public static void reset() { SESSION.reset(); world = null; hud = null; plates = null; }
    public static String scope() {
        Minecraft mc = Minecraft.getInstance();
        if (!bridgeAvailable() || world == null || mc.player == null || mc.getCurrentServer() == null) return "";
        String host = mc.getCurrentServer().ip.trim().toLowerCase(java.util.Locale.ROOT);
        if (ClientPreferences.isDefaultServer(host)) host = ClientPreferences.SERVER;
        return host + "/" + mc.player.getUUID() + "/" + world;
    }
}
