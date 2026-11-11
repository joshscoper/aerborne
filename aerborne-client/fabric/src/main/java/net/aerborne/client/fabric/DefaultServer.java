package net.aerborne.client.fabric;

import java.nio.file.Files;
import net.aerborne.client.protocol.ClientPreferences;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.nbt.NbtIo;
import org.slf4j.LoggerFactory;

public final class DefaultServer {
    private DefaultServer() { }
    public static void seed(Minecraft client) {
        if (ClientState.preferences.serverSeeded) return;
        try {
            var file = client.gameDirectory.toPath().resolve("servers.dat");
            // ServerList.load logs and swallows errors. Validate first so a corrupt file is never overwritten.
            if (Files.exists(file) && NbtIo.read(file) == null) return;
            ServerList servers = new ServerList(client);
            servers.load();
            boolean found = false;
            for (int i = 0; i < servers.size(); i++) found |= ClientPreferences.isDefaultServer(servers.get(i).ip);
            if (!found) {
                servers.add(new ServerData("Aerborne", ClientPreferences.SERVER, ServerData.Type.OTHER), false);
                servers.save();
                // save() also swallows errors: confirm persistence before marking initialization complete.
                ServerList verify = new ServerList(client); verify.load();
                for (int i = 0; i < verify.size(); i++) found |= ClientPreferences.isDefaultServer(verify.get(i).ip);
            }
            if (found) { ClientState.preferences.serverSeeded = true; ClientState.save(); }
        } catch (Exception e) {
            LoggerFactory.getLogger("Aerborne").warn("Could not add the default server; saved servers were preserved", e);
        }
    }
}
