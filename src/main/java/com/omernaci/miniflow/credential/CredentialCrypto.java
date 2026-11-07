package com.omernaci.miniflow.credential;

import com.omernaci.miniflow.common.exception.MiniFlowException;
import com.omernaci.miniflow.config.MiniFlowProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class CredentialCrypto {

    private static final Logger log = LoggerFactory.getLogger(CredentialCrypto.class);
    private static final String PREFIX = "v1:";
    private static final int IV_LENGTH = 12;
    private static final Path KEY_FILE = Path.of("data", "encryption.key");

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public CredentialCrypto(MiniFlowProperties props) {
        this.key = new SecretKeySpec(sha256(loadSecret(props.encryptionKey())), "AES");
    }

    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public String decrypt(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            throw new MiniFlowException("error.credential.decrypt");
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, all, 0, IV_LENGTH));
            byte[] plain = cipher.doFinal(all, IV_LENGTH, all.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (AEADBadTagException | IllegalArgumentException e) {
            throw new MiniFlowException("error.credential.decrypt");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String loadSecret(String configured) {
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        try {
            if (Files.exists(KEY_FILE)) {
                return Files.readString(KEY_FILE).trim();
            }
            byte[] bytes = new byte[32];
            new SecureRandom().nextBytes(bytes);
            String generated = Base64.getEncoder().encodeToString(bytes);
            Files.createDirectories(KEY_FILE.getParent());
            Files.writeString(KEY_FILE, generated);
            try {
                Files.setPosixFilePermissions(KEY_FILE, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException | IOException ignored) {
            }
            log.warn("Generated encryption key at {}. Back up this file or set MINIFLOW_ENCRYPTION_KEY; without it stored credentials cannot be decrypted",
                    KEY_FILE.toAbsolutePath());
            return generated;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read or write the encryption key file: " + KEY_FILE.toAbsolutePath(), e);
        }
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
