package tn.isetbizerte.pfe.hrbackend.modules.requests.entity;

import jakarta.persistence.*;
import tn.isetbizerte.pfe.hrbackend.common.enums.DocumentFulfillmentMode;
import tn.isetbizerte.pfe.hrbackend.common.enums.DocumentType;
import tn.isetbizerte.pfe.hrbackend.common.enums.RequestStatus;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;

import java.time.LocalDateTime;

@Entity
@Table(name = "document_requests")
public class DocumentRequest {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentType documentType;

    @Enumerated(EnumType.STRING)
    @Column
    private DocumentFulfillmentMode fulfillmentMode = DocumentFulfillmentMode.GENERATED;

    @Column(length = 1000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestStatus status = RequestStatus.PENDING;

    private String approvedBy;
    private String rejectedBy;
    private String canceledBy;
    private LocalDateTime canceledAt;

    private String hrNote;

    @Column(unique = true)
    private String verificationToken;

    // HR-provided attachment metadata (only used for fulfillmentMode=UPLOADED)
    private String attachmentFileName;
    private String attachmentContentType;
    private String attachmentStoragePath;
    private Long attachmentSizeBytes;
    @Column(length = 64)
    private String attachmentSha256;
    private LocalDateTime attachmentUploadedAt;
    private String attachmentUploadedBy;

    private LocalDateTime requestedAt;
    private LocalDateTime processedAt;

    public DocumentRequest() { this.requestedAt = LocalDateTime.now(); }

    // Getters & setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public DocumentType getDocumentType() { return documentType; }
    public void setDocumentType(DocumentType documentType) { this.documentType = documentType; }

    public DocumentFulfillmentMode getFulfillmentMode() { return fulfillmentMode; }
    public void setFulfillmentMode(DocumentFulfillmentMode fulfillmentMode) { this.fulfillmentMode = fulfillmentMode; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public RequestStatus getStatus() { return status; }
    public void setStatus(RequestStatus status) { this.status = status; }

    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }

    public String getRejectedBy() { return rejectedBy; }
    public void setRejectedBy(String rejectedBy) { this.rejectedBy = rejectedBy; }

    public String getCanceledBy() { return canceledBy; }
    public void setCanceledBy(String canceledBy) { this.canceledBy = canceledBy; }

    public LocalDateTime getCanceledAt() { return canceledAt; }
    public void setCanceledAt(LocalDateTime canceledAt) { this.canceledAt = canceledAt; }

    public String getHrNote() { return hrNote; }
    public void setHrNote(String hrNote) { this.hrNote = hrNote; }

    public String getVerificationToken() { return verificationToken; }
    public void setVerificationToken(String verificationToken) { this.verificationToken = verificationToken; }

    public String getAttachmentFileName() { return attachmentFileName; }
    public void setAttachmentFileName(String attachmentFileName) { this.attachmentFileName = attachmentFileName; }

    public String getAttachmentContentType() { return attachmentContentType; }
    public void setAttachmentContentType(String attachmentContentType) { this.attachmentContentType = attachmentContentType; }

    public String getAttachmentStoragePath() { return attachmentStoragePath; }
    public void setAttachmentStoragePath(String attachmentStoragePath) { this.attachmentStoragePath = attachmentStoragePath; }

    public Long getAttachmentSizeBytes() { return attachmentSizeBytes; }
    public void setAttachmentSizeBytes(Long attachmentSizeBytes) { this.attachmentSizeBytes = attachmentSizeBytes; }

    public String getAttachmentSha256() { return attachmentSha256; }
    public void setAttachmentSha256(String attachmentSha256) { this.attachmentSha256 = attachmentSha256; }

    public LocalDateTime getAttachmentUploadedAt() { return attachmentUploadedAt; }
    public void setAttachmentUploadedAt(LocalDateTime attachmentUploadedAt) { this.attachmentUploadedAt = attachmentUploadedAt; }

    public String getAttachmentUploadedBy() { return attachmentUploadedBy; }
    public void setAttachmentUploadedBy(String attachmentUploadedBy) { this.attachmentUploadedBy = attachmentUploadedBy; }

    public LocalDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(LocalDateTime requestedAt) { this.requestedAt = requestedAt; }

    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }

    public String getEmployeeFullName() {
        if (user != null && user.getPerson() != null)
            return user.getPerson().getFirstName() + " " + user.getPerson().getLastName();
        return "Unknown";
    }
    public String getEmployeeEmail() {
        if (user != null && user.getPerson() != null && user.getPerson().getEmail() != null)
            return user.getPerson().getEmail();
        return "";
    }
    public String getEmployeeDepartment() {
        if (user != null && user.getPerson() != null && user.getPerson().getDepartment() != null)
            return user.getPerson().getDepartment();
        return "";
    }
}
