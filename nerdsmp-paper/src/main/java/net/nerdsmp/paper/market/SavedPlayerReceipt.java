package net.nerdsmp.paper.market;

import java.io.DataInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/** Bounded, read-only NBT scanner. No server internals or NBT objects are loaded or modified. */
final class SavedPlayerReceipt {
    static final long MAX_COMPRESSED_BYTES = 16L * 1024 * 1024;
    static final long MAX_DECODED_BYTES = 64L * 1024 * 1024;
    private static final int MAX_DEPTH = 64;
    private static final int MAX_NODES = 100_000;
    private final DataInputStream input;
    private final String key;
    private String receipt;
    private int nodes;

    private SavedPlayerReceipt(DataInputStream input, String key) {
        this.input = input;
        this.key = key;
    }

    static void verify(Path file, String key, String expected) throws IOException {
        if (!Files.isRegularFile(file) || Files.size(file) > MAX_COMPRESSED_BYTES) {
            throw new IOException("Saved player file is missing, unsupported or oversized");
        }
        try (InputStream raw = Files.newInputStream(file);
             InputStream compressed = new LimitedInput(raw, MAX_COMPRESSED_BYTES);
             InputStream gzip = new GZIPInputStream(compressed);
             DataInputStream input = new DataInputStream(new LimitedInput(gzip, MAX_DECODED_BYTES))) {
            if (input.readUnsignedByte() != 10) throw new IOException("Player NBT root is not a compound");
            if (!input.readUTF().isEmpty()) throw new IOException("Unsupported named player NBT root");
            SavedPlayerReceipt reader = new SavedPlayerReceipt(input, key);
            reader.compound(0, 1);
            // Reaching EOF also validates the GZIP trailer; finding a nonce in a truncated file is insufficient.
            if (input.read() != -1) throw new IOException("Unexpected trailing player NBT data");
            if (expected == null || !expected.equals(reader.receipt)) {
                throw new IOException("Player save receipt is missing or stale (saving may be disabled or failed)");
            }
        }
    }

    // Context 1 is the root; 2 is its BukkitValues compound; 0 ignores unrelated payloads.
    private void compound(int depth, int context) throws IOException {
        checkDepth(depth);
        Set<String> names = new HashSet<>();
        while (true) {
            int type = input.readUnsignedByte();
            if (type == 0) return;
            countNode();
            String name = input.readUTF();
            if (!names.add(name)) throw new IOException("Duplicate NBT compound field");
            if (context == 1 && name.equals("BukkitValues")) {
                if (type != 10) throw new IOException("BukkitValues is not a compound");
                compound(depth + 1, 2);
            } else if (context == 2 && name.equals(key)) {
                if (type != 8) throw new IOException("Player save receipt is not a string");
                receipt = input.readUTF();
            } else {
                payload(type, depth + 1);
            }
        }
    }

    private void payload(int type, int depth) throws IOException {
        checkDepth(depth);
        int fixed = width(type);
        if (fixed > 0) {
            input.skipNBytes(fixed);
            return;
        }
        switch (type) {
            case 7 -> input.skipNBytes(arrayBytes(input.readInt(), 1));
            case 8 -> input.readUTF();
            case 9 -> {
                int element = input.readUnsignedByte();
                int count = input.readInt();
                if (count < 0 || element < 0 || element > 12 || element == 0 && count != 0) {
                    throw new IOException("Invalid NBT list");
                }
                int elementWidth = width(element);
                if (elementWidth > 0) {
                    input.skipNBytes(arrayBytes(count, elementWidth));
                } else {
                    if (count > MAX_NODES - nodes) throw new IOException("NBT list exceeds the node limit");
                    for (int i = 0; i < count; i++) {
                        countNode();
                        payload(element, depth + 1);
                    }
                }
            }
            case 10 -> compound(depth, 0);
            case 11 -> input.skipNBytes(arrayBytes(input.readInt(), 4));
            case 12 -> input.skipNBytes(arrayBytes(input.readInt(), 8));
            default -> throw new IOException("Unsupported NBT tag " + type);
        }
    }

    private static int width(int type) {
        return switch (type) {
            case 1 -> 1;
            case 2 -> 2;
            case 3, 5 -> 4;
            case 4, 6 -> 8;
            default -> 0;
        };
    }

    private static long arrayBytes(int count, int width) throws IOException {
        long bytes = (long) count * width;
        if (count < 0 || bytes > MAX_DECODED_BYTES) throw new IOException("Invalid or oversized NBT array/list");
        return bytes;
    }

    private static void checkDepth(int depth) throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("Player NBT exceeds the nesting limit");
    }

    private void countNode() throws IOException {
        if (++nodes > MAX_NODES) throw new IOException("Player NBT exceeds the node limit");
    }

    private static final class LimitedInput extends FilterInputStream {
        private final long maximum;
        private long consumed;

        private LimitedInput(InputStream input, long maximum) {
            super(input);
            this.maximum = maximum;
        }

        private void count(long amount) throws IOException {
            if (amount > 0 && (consumed += amount) > maximum) throw new IOException("Player NBT exceeds the byte limit");
        }

        @Override public int read() throws IOException {
            int value = in.read();
            if (value != -1) count(1);
            return value;
        }

        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
            int read = in.read(bytes, offset, (int) Math.min(length, maximum - consumed + 1));
            count(read);
            return read;
        }

        @Override public long skip(long amount) throws IOException {
            long skipped = in.skip(Math.min(amount, maximum - consumed + 1));
            count(skipped);
            return skipped;
        }
    }
}
