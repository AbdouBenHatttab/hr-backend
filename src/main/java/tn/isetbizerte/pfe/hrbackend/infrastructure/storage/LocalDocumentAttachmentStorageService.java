package tn.isetbizerte.pfe.hrbackend.infrastructure.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class LocalDocumentAttachmentStorageService implements DocumentAttachmentStorageService {

    private final Path baseDir;

    public LocalDocumentAttachmentStorageService(@Value("${app.storage.documents-dir:uploads/document-attachments}") String baseDir) {
        this.baseDir = Paths.get(baseDir).toAbsolutePath().normalize();
    }

    @Override
    public StoredAttachment store(long documentRequestId, String originalFileName, String contentType, byte[] bytes) throws IOException {
        String safeName = sanitizeFileName(originalFileName);
        String fileName = UUID.randomUUID() + (safeName.isBlank() ? "" : "_" + safeName);

        Path dir = baseDir.resolve(String.valueOf(documentRequestId)).normalize();
        if (!dir.startsWith(baseDir)) {
            throw new IOException("Invalid storage directory");
        }
        Files.createDirectories(dir);

        Path target = dir.resolve(fileName).normalize();
        if (!target.startsWith(dir)) {
            throw new IOException("Invalid storage path");
        }
        Files.write(target, bytes);

        String sha256 = sha256(bytes);
        String storagePath = baseDir.relativize(target).toString().replace('\\', '/');
        return new StoredAttachment(storagePath, safeName.isBlank() ? fileName : safeName, contentType, bytes.length, sha256);
    }

    @Override
    public byte[] read(String storagePath) throws IOException {
        Path p = resolve(storagePath);
        return Files.readAllBytes(p);
    }

    @Override
    public void deleteIfExists(String storagePath) throws IOException {
        if (storagePath == null || storagePath.isBlank()) return;
        Path p = resolve(storagePath);
        Files.deleteIfExists(p);
    }

    private Path resolve(String storagePath) throws IOException {
        if (storagePath == null || storagePath.isBlank()) {
            throw new IOException("Missing storage path");
        }
        Path p = baseDir.resolve(storagePath).toAbsolutePath().normalize();
        if (!p.startsWith(baseDir)) {
            throw new IOException("Invalid storage path");
        }
        return p;
    }

    private static String sanitizeFileName(String name) {
        if (name == null) return "";
        String trimmed = name.trim();
        if (trimmed.isBlank()) return "";
        return trimmed.replaceAll("[^a-zA-Z0-9._-]+", "_");
    }

    private static String sha256(byte[] bytes) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (Exception e) {
            throw new IOException("Unable to compute sha256", e);
        }
    }
}

