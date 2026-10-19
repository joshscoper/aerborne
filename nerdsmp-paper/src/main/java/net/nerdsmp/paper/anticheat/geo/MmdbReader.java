package net.nerdsmp.paper.anticheat.geo;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal reader for the MaxMind DB (.mmdb) format, which both DB-IP Lite and MaxMind GeoLite2 ship. Read-only and
 * offline: the file is memory-mapped, so a 130 MB city database costs no heap. Absolute reads only, so one instance
 * can be shared, but callers keep lookups off the main thread.
 */
public final class MmdbReader {
    private static final byte[] METADATA_MARKER = {(byte) 0xAB, (byte) 0xCD, (byte) 0xEF,
        'M', 'a', 'x', 'M', 'i', 'n', 'd', '.', 'c', 'o', 'm'};
    private static final int METADATA_SEARCH = 128 * 1024;
    private static final int MAX_DEPTH = 32;

    private final ByteBuffer buffer;
    private final int nodeCount;
    private final int recordSize;
    private final int ipVersion;
    private final int dataStart;
    private final int ipv4Start;
    private final Map<String, Object> metadata;

    public MmdbReader(ByteBuffer buffer) throws IOException {
        this.buffer = buffer;
        int marker = lastIndexOf(buffer, METADATA_MARKER);
        if (marker < 0) throw new IOException("Not a MaxMind DB file: no metadata section.");
        int metadataStart = marker + METADATA_MARKER.length;
        Object decoded = new Decoder(buffer, metadataStart).decode(metadataStart, 0).value;
        if (!(decoded instanceof Map<?, ?> map)) throw new IOException("Malformed MaxMind DB metadata.");
        @SuppressWarnings("unchecked") Map<String, Object> meta = (Map<String, Object>) map;
        metadata = meta;
        nodeCount = (int) number(meta, "node_count");
        recordSize = (int) number(meta, "record_size");
        ipVersion = (int) number(meta, "ip_version");
        if (recordSize != 24 && recordSize != 28 && recordSize != 32) throw new IOException("Unsupported record size " + recordSize + ".");
        if (ipVersion != 4 && ipVersion != 6) throw new IOException("Unsupported IP version " + ipVersion + ".");
        long treeSize = (long) recordSize * 2 / 8 * nodeCount;
        if (treeSize + 16 > marker) throw new IOException("Malformed MaxMind DB: search tree is larger than the file.");
        dataStart = (int) treeSize + 16;
        int node = 0;
        if (ipVersion == 6) { // IPv4 lives at ::a.b.c.d, 96 zero bits down the tree
            for (int bit = 0; bit < 96 && node < nodeCount; bit++) node = record(node, 0);
        }
        ipv4Start = node;
    }

    public static MmdbReader open(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            if (channel.size() > Integer.MAX_VALUE) throw new IOException("Database is larger than 2 GB.");
            return new MmdbReader(channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size()));
        }
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    public String databaseType() {
        return metadata.get("database_type") instanceof String type ? type : "";
    }

    /** Build time in epoch seconds, or 0 when the file does not say. */
    public long buildEpoch() {
        return metadata.get("build_epoch") instanceof Number epoch ? epoch.longValue() : 0L;
    }

    /** The data record for an address, or null when the database has none. */
    public Map<String, Object> lookup(InetAddress address) throws IOException {
        byte[] raw = address.getAddress();
        if (raw.length == 16 && ipVersion == 4) {
            if (!isIpv4Mapped(raw)) return null;
            raw = java.util.Arrays.copyOfRange(raw, 12, 16);
        }
        int node = raw.length == 4 && ipVersion == 6 ? ipv4Start : 0;
        int bits = raw.length * 8;
        for (int bit = 0; bit < bits && node < nodeCount; bit++) {
            node = record(node, (raw[bit >> 3] >> (7 - (bit & 7))) & 1);
        }
        if (node == nodeCount) return null;
        if (node < nodeCount) throw new IOException("Malformed MaxMind DB: lookup ended inside the search tree.");
        int offset = node - nodeCount - 16;
        Object value = new Decoder(buffer, dataStart).decode(dataStart + offset, 0).value;
        if (!(value instanceof Map<?, ?> map)) return null;
        @SuppressWarnings("unchecked") Map<String, Object> record = (Map<String, Object>) map;
        return record;
    }

    private int record(int node, int side) throws IOException {
        int base = node * recordSize * 2 / 8;
        try {
            return switch (recordSize) {
                case 24 -> uint(buffer, base + side * 3, 3);
                case 28 -> side == 0
                    ? ((buffer.get(base + 3) & 0xF0) << 20) | uint(buffer, base, 3)
                    : ((buffer.get(base + 3) & 0x0F) << 24) | uint(buffer, base + 4, 3);
                default -> uint(buffer, base + side * 4, 4);
            };
        } catch (IndexOutOfBoundsException truncated) {
            throw new IOException("Malformed MaxMind DB: truncated search tree.");
        }
    }

    private static boolean isIpv4Mapped(byte[] raw) {
        for (int i = 0; i < 10; i++) if (raw[i] != 0) return false;
        return raw[10] == (byte) 0xFF && raw[11] == (byte) 0xFF;
    }

    private static long number(Map<String, Object> meta, String key) throws IOException {
        if (meta.get(key) instanceof Number value) return value.longValue();
        throw new IOException("MaxMind DB metadata is missing " + key + ".");
    }

    private static int lastIndexOf(ByteBuffer buffer, byte[] needle) {
        int limit = buffer.limit();
        int floor = Math.max(0, limit - METADATA_SEARCH);
        outer:
        for (int start = limit - needle.length; start >= floor; start--) {
            for (int i = 0; i < needle.length; i++) if (buffer.get(start + i) != needle[i]) continue outer;
            return start;
        }
        return -1;
    }

    private static int uint(ByteBuffer buffer, int offset, int length) {
        int value = 0;
        for (int i = 0; i < length; i++) value = (value << 8) | (buffer.get(offset + i) & 0xFF);
        return value;
    }

    /** Decodes the MaxMind DB data format. Pointers are relative to {@code base}, the start of the data section. */
    static final class Decoder {
        private final ByteBuffer buffer;
        private final int base;

        Decoder(ByteBuffer buffer, int base) {
            this.buffer = buffer;
            this.base = base;
        }

        record Result(Object value, int next) {}

        Result decode(int offset, int depth) throws IOException {
            if (depth > MAX_DEPTH) throw new IOException("Malformed MaxMind DB: data nested too deeply.");
            try {
                int control = buffer.get(offset++) & 0xFF;
                int type = control >>> 5;
                if (type == 1) { // pointer: follow it, then carry on after the pointer itself
                    int size = (control >>> 3) & 0x3;
                    int high = control & 0x7;
                    int target = switch (size) {
                        case 0 -> (high << 8) | uint(buffer, offset, 1);
                        case 1 -> ((high << 16) | uint(buffer, offset, 2)) + 2048;
                        case 2 -> ((high << 24) | uint(buffer, offset, 3)) + 526_336;
                        default -> uint(buffer, offset, 4);
                    };
                    if (target < 0 || base + target >= buffer.limit()) throw new IOException("Malformed MaxMind DB: pointer out of range.");
                    return new Result(decode(base + target, depth + 1).value, offset + size + 1);
                }
                if (type == 0) type = 7 + (buffer.get(offset++) & 0xFF);
                int size = control & 0x1F;
                if (size >= 29) {
                    int extra = size - 28;
                    int bytes = uint(buffer, offset, extra);
                    offset += extra;
                    size = switch (extra) { case 1 -> 29 + bytes; case 2 -> 285 + bytes; default -> 65_821 + bytes; };
                }
                return switch (type) {
                    case 2 -> new Result(string(offset, size), offset + size);
                    case 3 -> { requireSize(size, 8); yield new Result(buffer.getDouble(offset), offset + 8); }
                    case 4 -> { byte[] bytes = new byte[size]; buffer.get(offset, bytes); yield new Result(bytes, offset + size); }
                    case 5, 6, 9 -> new Result(unsigned(offset, size).longValue(), offset + size);
                    case 10 -> new Result(unsigned(offset, size), offset + size);
                    case 8 -> new Result(unsigned(offset, size).intValue(), offset + size);
                    case 7 -> map(offset, size, depth);
                    case 11 -> array(offset, size, depth);
                    case 14 -> new Result(size != 0, offset);
                    case 15 -> { requireSize(size, 4); yield new Result(buffer.getFloat(offset), offset + 4); }
                    default -> throw new IOException("Malformed MaxMind DB: unknown data type " + type + ".");
                };
            } catch (IndexOutOfBoundsException truncated) {
                throw new IOException("Malformed MaxMind DB: truncated data section.");
            }
        }

        private Result map(int offset, int size, int depth) throws IOException {
            Map<String, Object> map = new LinkedHashMap<>();
            for (int i = 0; i < size; i++) {
                Result key = decode(offset, depth + 1);
                if (!(key.value instanceof String name)) throw new IOException("Malformed MaxMind DB: map key is not text.");
                Result value = decode(key.next, depth + 1);
                map.put(name, value.value);
                offset = value.next;
            }
            return new Result(map, offset);
        }

        private Result array(int offset, int size, int depth) throws IOException {
            List<Object> list = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                Result value = decode(offset, depth + 1);
                list.add(value.value);
                offset = value.next;
            }
            return new Result(list, offset);
        }

        private String string(int offset, int size) {
            byte[] bytes = new byte[size];
            buffer.get(offset, bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        }

        private BigInteger unsigned(int offset, int size) throws IOException {
            if (size > 16) throw new IOException("Malformed MaxMind DB: integer wider than 128 bits.");
            byte[] bytes = new byte[size];
            buffer.get(offset, bytes);
            return new BigInteger(1, bytes);
        }

        private static void requireSize(int size, int expected) throws IOException {
            if (size != expected) throw new IOException("Malformed MaxMind DB: number has size " + size + ".");
        }
    }
}
