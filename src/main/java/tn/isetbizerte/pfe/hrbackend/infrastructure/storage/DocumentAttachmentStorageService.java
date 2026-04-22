package tn.isetbizerte.pfe.hrbackend.infrastructure.storage;

import java.io.IOException;

public interface DocumentAttachmentStorageService {

    StoredAttachment store(long documentRequestId, String originalFileName, String contentType, byte[] bytes) throws IOException;

    byte[] read(String storagePath) throws IOException;

    void deleteIfExists(String storagePath) throws IOException;
}

