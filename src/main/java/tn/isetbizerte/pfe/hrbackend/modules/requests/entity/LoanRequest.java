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

    private String approvedBy;
    private String rejectedBy;

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

    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }

    public String getRejectedBy() { return rejectedBy; }
    public void setRejectedBy(String rejectedBy) { this.rejectedBy = rejectedBy; }

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
