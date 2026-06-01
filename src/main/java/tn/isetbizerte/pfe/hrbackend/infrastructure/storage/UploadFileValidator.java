package tn.isetbizerte.pfe.hrbackend.infrastructure.storage;

import tn.isetbizerte.pfe.hrbackend.common.exception.BadRequestException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Validates uploaded files before storage using extension and detected content-type checks.
 */
public final class UploadFileValidator {

    private static final long MAX_BYTES = 15L * 1024L * 1024L;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "png", "jpg", "jpeg", "doc", "docx");

    private UploadFileValidator() {
    }

    public static ValidatedFile validate(String originalFileName, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new BadRequestException("File is required.");
        }
        if (bytes.length > MAX_BYTES) {
            throw new BadRequestException("File too large. Max 15MB.");
        }

        String extension = extensionOf(originalFileName);
        if (extension.isBlank()) {
            throw new BadRequestException("File extension is required. Allowed types: PDF, PNG, JPG, JPEG, DOC, DOCX.");
        }
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BadRequestException("Unsupported file extension. Allowed types: PDF, PNG, JPG, JPEG, DOC, DOCX.");
        }

        DetectedFileType detected = detect(bytes);
        if (detected == null) {
            throw new BadRequestException("Unsupported or invalid file content. Upload a valid PDF, image, DOC, or DOCX file.");
        }
        if (!detected.extensions().contains(extension)) {
            throw new BadRequestException("File extension does not match the uploaded file content.");
        }

        return new ValidatedFile(detected.contentType());
    }

    private static String extensionOf(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            return "";
        }

        String name = originalFileName.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }

        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }

        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static DetectedFileType detect(byte[] bytes) {
        if (startsWith(bytes, 0x25, 0x50, 0x44, 0x46, 0x2D)) {
            return new DetectedFileType("application/pdf", Set.of("pdf"));
        }
        if (startsWith(bytes, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return new DetectedFileType("image/png", Set.of("png"));
        }
        if (startsWith(bytes, 0xFF, 0xD8, 0xFF)) {
            return new DetectedFileType("image/jpeg", Set.of("jpg", "jpeg"));
        }
        if (startsWith(bytes, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1)) {
            return new DetectedFileType("application/msword", Set.of("doc"));
        }
        if (isDocx(bytes)) {
            return new DetectedFileType(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    Set.of("docx")
            );
        }
        return null;
    }

    private static boolean startsWith(byte[] bytes, int... signature) {
        if (bytes.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((bytes[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isDocx(byte[] bytes) {
        if (!startsWith(bytes, 0x50, 0x4B, 0x03, 0x04)
                && !startsWith(bytes, 0x50, 0x4B, 0x05, 0x06)
                && !startsWith(bytes, 0x50, 0x4B, 0x07, 0x08)) {
            return false;
        }

        boolean hasContentTypes = false;
        boolean hasWordDocument = false;

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if ("[Content_Types].xml".equals(name)) {
                    hasContentTypes = true;
                }
                if ("word/document.xml".equals(name)) {
                    hasWordDocument = true;
                }
                if (hasContentTypes && hasWordDocument) {
                    return true;
                }
            }
        } catch (IOException ignored) {
            return false;
        }

        return false;
    }

    private record DetectedFileType(String contentType, Set<String> extensions) {
    }

    public record ValidatedFile(String contentType) {
    }
}
