package net.nerdsmp.paper.vote;

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
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;

/**
 * The RSA key pair for version 1 votes, in NuVotifier's layout: {@code public.key} and {@code private.key}, each
 * one line of base64. A server moving over from NuVotifier can copy its {@code rsa} folder here and keep the
 * public key it gave the sites. Only the owner can read the private key.
 */
final class VotifierKeys {
    private VotifierKeys() {}

    static KeyPair loadOrCreate(Path directory) throws IOException, GeneralSecurityException {
        if (Files.isSymbolicLink(directory)) throw new IOException("The Votifier key folder must not be a symbolic link");
        Files.createDirectories(directory);
        Path publicFile = directory.resolve("public.key");
        Path privateFile = directory.resolve("private.key");
        if (!Files.exists(publicFile, LinkOption.NOFOLLOW_LINKS) && !Files.exists(privateFile, LinkOption.NOFOLLOW_LINKS)) {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            writePrivate(privateFile, Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
            Files.writeString(publicFile, Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()), StandardCharsets.US_ASCII);
            return pair;
        }
        KeyFactory rsa = KeyFactory.getInstance("RSA");
        return new KeyPair(rsa.generatePublic(new X509EncodedKeySpec(read(publicFile))), rsa.generatePrivate(new PKCS8EncodedKeySpec(read(privateFile))));
    }

    private static byte[] read(Path file) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > 8192) throw new IOException("Missing or invalid " + file.getFileName());
        try { return Base64.getMimeDecoder().decode(Files.readString(file, StandardCharsets.US_ASCII).strip()); }
        catch (IllegalArgumentException invalid) { throw new IOException(file.getFileName() + " is not base64"); }
    }

    private static void writePrivate(Path file, String text) throws IOException {
        if (Files.getFileAttributeView(file.getParent(), PosixFileAttributeView.class) != null) {
            Files.createFile(file, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        } else {
            Files.createFile(file);
            AclFileAttributeView acl = Files.getFileAttributeView(file, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
            if (acl == null) { Files.delete(file); throw new IOException("File system cannot protect the Votifier private key"); }
            acl.setAcl(List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(acl.getOwner())
                .setPermissions(EnumSet.allOf(AclEntryPermission.class)).build()));
        }
        Files.writeString(file, text, StandardCharsets.US_ASCII, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
    }
}
