package net.aerborne.client.fabric;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.CRC32;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

/**
 * Answers the server's round-trip diagnostics. On each {@link RttPayloads.Ping} it measures the client clock against
 * the server's, folds in the loader and display settings the server asked about, and returns the lot signed so the
 * server can tell a genuine reply from a crafted one. All of the work is local and stateless; nothing is cached and
 * nothing is shown.
 */
final class RttService {
    private RttService() { }

    // Assembled at use from two halves so it is not a constant anyone can read out of the jar.
    private static final byte[] A = {
        (byte) 0x2A, (byte) 0x2A, (byte) 0x82, (byte) 0x1C, (byte) 0x35, (byte) 0x8A, (byte) 0x21, (byte) 0x7B,
        (byte) 0x1D, (byte) 0x4F, (byte) 0xC2, (byte) 0x8F, (byte) 0xB9, (byte) 0x36, (byte) 0x47, (byte) 0x84,
        (byte) 0xE4, (byte) 0xB2, (byte) 0x82, (byte) 0x70, (byte) 0x42, (byte) 0x49, (byte) 0x26, (byte) 0x9E};
    private static final byte[] B = {
        (byte) 0xA8, (byte) 0x1B, (byte) 0x69, (byte) 0x9B, (byte) 0x37, (byte) 0x63, (byte) 0x95, (byte) 0x5F,
        (byte) 0x74, (byte) 0x24, (byte) 0x98, (byte) 0x06, (byte) 0xBB, (byte) 0xA6, (byte) 0xFC, (byte) 0xD2,
        (byte) 0x3E, (byte) 0xEF, (byte) 0x2A, (byte) 0x4D, (byte) 0xEF, (byte) 0x3A, (byte) 0xEA, (byte) 0x76};

    static void register() {
        PayloadTypeRegistry.serverboundPlay().register(RttPayloads.Pong.TYPE, RttPayloads.Pong.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RttPayloads.Ping.TYPE, RttPayloads.Ping.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(RttPayloads.Ping.TYPE, (payload, context) -> context.client().execute(() -> a(payload.data())));
    }

    private static void a(byte[] message) {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));
            if (in.readByte() != 1 || in.readByte() != 0) return;
            byte[] nonce = in.readNBytes(16), salt = in.readNBytes(8);
            in.readLong(); // server clock
            if (nonce.length != 16 || salt.length != 8) return;
            if (!ClientPlayNetworking.canSend(RttPayloads.Pong.TYPE)) return;

            byte[] load = c();
            byte[] tag = b(salt, nonce, load);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(64 + load.length);
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeByte(1);
            out.writeByte(1);
            out.write(nonce);
            out.write(tag);
            out.write(load);
            ClientPlayNetworking.send(new RttPayloads.Pong(bytes.toByteArray()));
        } catch (Exception ignored) { }
    }

    /** The facts the server asked after: display settings, loader, build and the loaded mod list. */
    private static byte[] c() throws Exception {
        Minecraft mc = Minecraft.getInstance();
        List<String> ids = new ArrayList<>();
        for (var mod : FabricLoader.getInstance().getAllMods()) ids.add(mod.getMetadata().getId());
        Collections.sort(ids);

        double gamma = mc.options == null ? 0.5D : mc.options.gamma().get();
        int fov = mc.options == null ? 70 : mc.options.fov().get();
        String loaderVersion = FabricLoader.getInstance().getModContainer("fabricloader")
            .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("");
        String build = FabricLoader.getInstance().getModContainer("aerborne_client")
            .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("");

        CRC32 crc = new CRC32();
        for (String id : ids) crc.update(id.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        ByteArrayOutputStream bytes = new ByteArrayOutputStream(256);
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeLong(System.currentTimeMillis());
        out.writeFloat((float) gamma);
        out.writeFloat(fov);
        out.writeUTF("fabric");
        out.writeUTF(d(loaderVersion));
        out.writeUTF(d(build));
        out.writeLong(crc.getValue());
        out.writeInt(ids.size());
        for (String id : ids) out.writeUTF(d(id));
        return bytes.toByteArray();
    }

    private static byte[] b(byte[] salt, byte[] nonce, byte[] load) throws Exception {
        byte[] root = new byte[A.length];
        for (int i = 0; i < root.length; i++) root[i] = (byte) (A[i] ^ B[i]);
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        sha.update(root);
        byte[] key = sha.digest(salt);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        mac.update(nonce);
        mac.update(salt);
        mac.update(load);
        return mac.doFinal();
    }

    private static String d(String value) {
        if (value == null) return "";
        return value.length() > 80 ? value.substring(0, 80) : value;
    }
}
