package net.nerdsmp.paper.webapi;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;

/** Random bearer credential, separate from Watchdog. Only its digest stays in the HTTP listener. */
final class ApiToken {
    private final byte[] digest;

    private ApiToken(String token) {
        if (token == null || !token.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid web API token file");
        digest = hash(token);
    }

    static ApiToken of(String token) { return new ApiToken(token); }

    static ApiToken loadOrCreate(Path path) throws IOException {
        rejectLink(path.getParent());
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            String token = HexFormat.of().formatHex(random);
            Arrays.fill(random, (byte) 0);
            if (Files.getFileAttributeView(path.getParent(), PosixFileAttributeView.class) != null) {
                Files.createFile(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            } else {
                Files.createFile(path);
                AclFileAttributeView acl = Files.getFileAttributeView(path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
                if (acl == null) {
                    Files.delete(path); // The just-created empty file contains no credential.
                    throw new IOException("File system cannot protect the API token; use a supported private server directory");
                }
                acl.setAcl(List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(acl.getOwner())
                    .setPermissions(EnumSet.allOf(AclEntryPermission.class)).build()));
            }
            Files.writeString(path, token + "\n", StandardCharsets.US_ASCII,
                StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
        }
        rejectLink(path);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > 66) {
            throw new IOException("Invalid security/web-api-token.txt; existing file was preserved");
        }
        String raw = Files.readString(path, StandardCharsets.US_ASCII);
        if (raw.endsWith("\r\n")) raw = raw.substring(0, raw.length() - 2);
        else if (raw.endsWith("\n")) raw = raw.substring(0, raw.length() - 1);
        return new ApiToken(raw);
    }

    static void rejectLink(Path path) throws IOException {
        if (path == null || Files.isSymbolicLink(path)) throw new IOException("Web API paths must not be symbolic links");
    }

    boolean verify(String header) {
        if (header == null || header.length() != 71 || !header.regionMatches(true, 0, "Bearer ", 0, 7)) return false;
        String raw = header.substring(7);
        if (!raw.matches("[a-f0-9]{64}")) return false;
        byte[] candidate = hash(raw);
        try { return MessageDigest.isEqual(digest, candidate); }
        finally { Arrays.fill(candidate, (byte) 0); }
    }

    private static byte[] hash(String token) {
        try { return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)); }
        catch (NoSuchAlgorithmException unavailable) { throw new IllegalStateException("SHA-256 unavailable", unavailable); }
    }
}
