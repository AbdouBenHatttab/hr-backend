package tn.isetbizerte.pfe.hrbackend.infrastructure.storage;

public class StoredAttachment {
    private final String storagePath;
    private final String fileName;
    private final String contentType;
    private final long sizeBytes;
    private final String sha256;

    public StoredAttachment(String storagePath, String fileName, String contentType, long sizeBytes, String sha256) {
        this.storagePath = storagePath;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }
}

