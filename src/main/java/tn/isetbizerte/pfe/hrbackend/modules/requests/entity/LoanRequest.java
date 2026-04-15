package tn.isetbizerte.pfe.hrbackend.modules.requests.entity;

import jakarta.persistence.*;
import tn.isetbizerte.pfe.hrbackend.common.enums.LoanType;
import tn.isetbizerte.pfe.hrbackend.common.enums.RequestStatus;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "loan_requests")
public class LoanRequest {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoanType loanType;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private Integer repaymentMonths;

    @Column(length = 1000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestStatus status = RequestStatus.PENDING;

    private String hrNote;

    // ── Scoring ──────────────────────────────────────────
    @Column(precision = 10, scale = 2)
    private BigDecimal monthlyInstallment;   // amount / repaymentMonths
    private Integer riskScore;               // 0-100
    private String  systemRecommendation;    // APPROVE / REVIEW / REJECT
    @Column(length = 2000)
    private String  decisionReason;
    private Boolean meetingRequired;  // nullable — set by scoring engine

    private LocalDateTime meetingAt;
    @Column(length = 1000)
    private String meetingNote;

    private String attachmentFileName;
    private String attachmentContentType;
    private String attachmentStoragePath;
    private Long attachmentSizeBytes;
    @Column(length = 64)
    private String attachmentSha256;
    private LocalDateTime attachmentUploadedAt;
    private String attachmentUploadedBy;

    private String approvedBy;
    private LocalDateTime approvedAt;
    private String rejectedBy;
    private LocalDateTime rejectedAt;
    @Column(length = 1000)
    private String hrDecisionReason;
    private String hrDecisionStage;

    @Column(length = 1000)
    private String cancellationReason;
    private String canceledBy;
    private LocalDateTime canceledAt;

    private String meetingScheduledBy;
    private LocalDateTime meetingScheduledAt;

    @Column(unique = true)
    private String verificationToken;

    private LocalDateTime requestedAt;
    private LocalDateTime processedAt;

    public LoanRequest() { this.requestedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public LoanType getLoanType() { return loanType; }
    public void setLoanType(LoanType loanType) { this.loanType = loanType; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public Integer getRepaymentMonths() { return repaymentMonths; }
    public void setRepaymentMonths(Integer repaymentMonths) { this.repaymentMonths = repaymentMonths; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public RequestStatus getStatus() { return status; }
    public void setStatus(RequestStatus status) { this.status = status; }

    public String getHrNote() { return hrNote; }
    public void setHrNote(String hrNote) { this.hrNote = hrNote; }

    public BigDecimal getMonthlyInstallment() { return monthlyInstallment; }
    public void setMonthlyInstallment(BigDecimal m) { this.monthlyInstallment = m; }

    public Integer getRiskScore() { return riskScore; }
    public void setRiskScore(Integer riskScore) { this.riskScore = riskScore; }

    public String getSystemRecommendation() { return systemRecommendation; }
    public void setSystemRecommendation(String r) { this.systemRecommendation = r; }

    public String getDecisionReason() { return decisionReason; }
    public void setDecisionReason(String d) { this.decisionReason = d; }

    public Boolean getMeetingRequired() { return meetingRequired; }
    public void setMeetingRequired(Boolean m) { this.meetingRequired = m; }

    public LocalDateTime getMeetingAt() { return meetingAt; }
    public void setMeetingAt(LocalDateTime meetingAt) { this.meetingAt = meetingAt; }

    public String getMeetingNote() { return meetingNote; }
    public void setMeetingNote(String meetingNote) { this.meetingNote = meetingNote; }

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

    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }

    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }

    public String getRejectedBy() { return rejectedBy; }
    public void setRejectedBy(String rejectedBy) { this.rejectedBy = rejectedBy; }

    public LocalDateTime getRejectedAt() { return rejectedAt; }
    public void setRejectedAt(LocalDateTime rejectedAt) { this.rejectedAt = rejectedAt; }

    public String getHrDecisionReason() { return hrDecisionReason; }
    public void setHrDecisionReason(String hrDecisionReason) { this.hrDecisionReason = hrDecisionReason; }

    public String getHrDecisionStage() { return hrDecisionStage; }
    public void setHrDecisionStage(String hrDecisionStage) { this.hrDecisionStage = hrDecisionStage; }

    public String getCancellationReason() { return cancellationReason; }
    public void setCancellationReason(String cancellationReason) { this.cancellationReason = cancellationReason; }

    public String getCanceledBy() { return canceledBy; }
    public void setCanceledBy(String canceledBy) { this.canceledBy = canceledBy; }

    public LocalDateTime getCanceledAt() { return canceledAt; }
    public void setCanceledAt(LocalDateTime canceledAt) { this.canceledAt = canceledAt; }

    public String getMeetingScheduledBy() { return meetingScheduledBy; }
    public void setMeetingScheduledBy(String meetingScheduledBy) { this.meetingScheduledBy = meetingScheduledBy; }

    public LocalDateTime getMeetingScheduledAt() { return meetingScheduledAt; }
    public void setMeetingScheduledAt(LocalDateTime meetingScheduledAt) { this.meetingScheduledAt = meetingScheduledAt; }

    public String getVerificationToken() { return verificationToken; }
    public void setVerificationToken(String verificationToken) { this.verificationToken = verificationToken; }

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
        if (user != null && user.getPerson() != null) return user.getPerson().getEmail();
        return "";
    }
    public String getEmployeeDepartment() {
        if (user != null && user.getPerson() != null && user.getPerson().getDepartment() != null)
            return user.getPerson().getDepartment();
        return "";
    }
}
