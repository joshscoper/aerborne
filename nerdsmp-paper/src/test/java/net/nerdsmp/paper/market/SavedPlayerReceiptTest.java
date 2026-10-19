package net.nerdsmp.paper.market;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class SavedPlayerReceiptTest {
    @TempDir Path directory;
    private static final String KEY = "nerdsmp:market_save_receipt";
    private static final String RECEIPT = "2e2b7ad4-5350-4486-a8eb-efbb693870f2";

    @Test
    void verifiesTheRootBukkitReceiptAcrossUnrelatedInventoryAndArrayData() throws Exception {
        Path file = saved(out -> {
            field(out, 9, "Inventory"); out.writeByte(10); out.writeInt(1);
            field(out, 8, "id"); out.writeUTF("minecraft:diamond");
            field(out, 1, "Slot"); out.writeByte(0); out.writeByte(0);
            receipt(out, RECEIPT);
            field(out, 7, "bytes"); out.writeInt(3); out.write(new byte[3]);
            field(out, 11, "ints"); out.writeInt(1); out.writeInt(1);
            field(out, 12, "longs"); out.writeInt(1); out.writeLong(2);
            field(out, 9, "Pos"); out.writeByte(6); out.writeInt(3);
            out.writeDouble(1); out.writeDouble(64); out.writeDouble(-1);
        });

        assertDoesNotThrow(() -> verify(file));
    }

    @Test
    void staleOrMissingReceiptsDoNotAcknowledgeASilentFailedSave() throws Exception {
        Path stale = saved(out -> receipt(out, "earlier-save"));
        Path missing = saved(out -> { });
        assertThrows(IOException.class, () -> verify(stale));
        assertThrows(IOException.class, () -> verify(missing));
        assertThrows(IOException.class, () -> verify(directory.resolve("missing.dat")));
    }

    @Test
    void findingTheReceiptDoesNotHideATruncatedGzipTrailer() throws Exception {
        Path file = saved(out -> receipt(out, RECEIPT));
        byte[] complete = Files.readAllBytes(file);
        Files.write(file, Arrays.copyOf(complete, complete.length - 4));
        assertThrows(IOException.class, () -> verify(file));
    }

    @Test
    void malformedPayloadAfterTheReceiptStillFails() throws Exception {
        Path file = saved(out -> { receipt(out, RECEIPT); field(out, 13, "unsupported"); });
        assertThrows(IOException.class, () -> verify(file));
    }

    @Test
    void duplicateBukkitCompoundsOrReceiptFieldsAreRejected() throws Exception {
        Path compounds = saved(out -> { receipt(out, RECEIPT); receipt(out, RECEIPT); });
        Path fields = saved(out -> {
            field(out, 10, "BukkitValues");
            field(out, 8, KEY); out.writeUTF(RECEIPT);
            field(out, 8, KEY); out.writeUTF(RECEIPT); out.writeByte(0);
        });
        assertThrows(IOException.class, () -> verify(compounds));
        assertThrows(IOException.class, () -> verify(fields));
    }

    @Test
    void aNestedOrWronglyTypedReceiptCannotSpoofTheRootReceipt() throws Exception {
        Path nested = saved(out -> { field(out, 10, "nested"); receipt(out, RECEIPT); out.writeByte(0); });
        Path wrongType = saved(out -> {
            field(out, 10, "BukkitValues"); field(out, 3, KEY); out.writeInt(1); out.writeByte(0);
        });
        assertThrows(IOException.class, () -> verify(nested));
        assertThrows(IOException.class, () -> verify(wrongType));
    }

    @Test
    void maliciousArrayLengthsAndExcessiveNestingFailBeforeAllocation() throws Exception {
        Path oversized = saved(out -> { field(out, 12, "oversized"); out.writeInt(Integer.MAX_VALUE); });
        Path negative = saved(out -> { field(out, 7, "negative"); out.writeInt(-1); });
        Path deep = saved(out -> {
            for (int i = 0; i < 70; i++) field(out, 10, "child");
            receipt(out, RECEIPT);
            for (int i = 0; i < 70; i++) out.writeByte(0);
        });
        assertThrows(IOException.class, () -> verify(oversized));
        assertThrows(IOException.class, () -> verify(negative));
        assertThrows(IOException.class, () -> verify(deep));
    }

    @Test
    void trailingNbtAfterTheRootIsRejected() throws Exception {
        Path file = saved(out -> { receipt(out, RECEIPT); out.writeByte(0); out.writeByte(1); });
        assertThrows(IOException.class, () -> verify(file));
    }

    @Test
    void malformedUtfInAnUnrelatedTagCannotHideAfterAValidReceipt() throws Exception {
        Path file = saved(out -> {
            receipt(out, RECEIPT);
            field(out, 8, "broken-string"); out.writeShort(1); out.writeByte(0xC0);
        });
        assertThrows(IOException.class, () -> verify(file));
    }

    private Path saved(Writer writer) throws IOException {
        Path file = directory.resolve(UUID.randomUUID() + ".dat");
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(file)))) {
            out.writeByte(10); out.writeUTF("");
            writer.write(out);
            out.writeByte(0);
        }
        return file;
    }

    private static void receipt(DataOutputStream out, String value) throws IOException {
        field(out, 10, "BukkitValues");
        field(out, 8, KEY); out.writeUTF(value); out.writeByte(0);
    }

    private static void field(DataOutputStream out, int type, String name) throws IOException {
        out.writeByte(type); out.writeUTF(name);
    }

    private static void verify(Path file) throws IOException {
        SavedPlayerReceipt.verify(file, KEY, RECEIPT);
    }

    @FunctionalInterface private interface Writer {
        void write(DataOutputStream out) throws IOException;
    }
}
