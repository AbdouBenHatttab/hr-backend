package tn.isetbizerte.pfe.hrbackend.modules.employee.entity;

import jakarta.persistence.*;
import tn.isetbizerte.pfe.hrbackend.common.enums.ApprovalDecision;
import tn.isetbizerte.pfe.hrbackend.common.enums.LeaveStatus;
import tn.isetbizerte.pfe.hrbackend.common.enums.LeaveType;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "leave_requests")
public class LeaveRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeaveType leaveType;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private Integer numberOfDays;

    @Column(length = 1000)
    private String reason;

    @Column(nullable = false)
    private LocalDateTime requestDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalDecision teamLeaderDecision;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalDecision hrDecision;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeaveStatus status;

    private LocalDate approvalDate;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // ── Scoring fields — computed on submission ──────────────────
    private Integer systemScore;           // 0-100
    private String  systemRecommendation;  // APPROVE / REVIEW / REJECT
    @Column(length = 2000)
    private String  decisionReason;        // human-readable explanation

    private String approvedBy;
    private String rejectedBy;

    // Running total of leave days taken in last 12 months (updated on approval)
    // nullable=true so Hibernate can add this column to existing tables without failing
    @Column(nullable = true)
    private Integer totalLeaveTakenLast12Months = 0;

    // QR verification token — generated when HR gives final approval
    @Column(unique = true)
    private String verificationToken;

    public LeaveRequest() {
        this.requestDate = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.teamLeaderDecision = ApprovalDecision.PENDING;
        this.hrDecision = ApprovalDecision.PENDING;
        this.status = LeaveStatus.PENDING;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public LeaveType getLeaveType() {
        return leaveType;
    }

    public void setLeaveType(LeaveType leaveType) {
        this.leaveType = leaveType;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public Integer getNumberOfDays() {
        return numberOfDays;
    }

    public void setNumberOfDays(Integer numberOfDays) {
        this.numberOfDays = numberOfDays;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LocalDateTime getRequestDate() {
        return requestDate;
    }

    public void setRequestDate(LocalDateTime requestDate) {
        this.requestDate = requestDate;
    }

    public ApprovalDecision getTeamLeaderDecision() {
        return teamLeaderDecision;
    }

    public void setTeamLeaderDecision(ApprovalDecision teamLeaderDecision) {
        this.teamLeaderDecision = teamLeaderDecision;
    }

    public ApprovalDecision getHrDecision() {
        return hrDecision;
    }

    public void setHrDecision(ApprovalDecision hrDecision) {
        this.hrDecision = hrDecision;
    }

    public LeaveStatus getStatus() {
        return status;
    }

    public void setStatus(LeaveStatus status) {
        this.status = status;
    }

    public LocalDate getApprovalDate() {
        return approvalDate;
    }

    public void setApprovalDate(LocalDate approvalDate) {
        this.approvalDate = approvalDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getSystemScore() { return systemScore; }
    public void setSystemScore(Integer systemScore) { this.systemScore = systemScore; }

    public String getSystemRecommendation() { return systemRecommendation; }
    public void setSystemRecommendation(String r) { this.systemRecommendation = r; }

    public String getDecisionReason() { return decisionReason; }
    public void setDecisionReason(String decisionReason) { this.decisionReason = decisionReason; }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public String getRejectedBy() {
        return rejectedBy;
    }

    public void setRejectedBy(String rejectedBy) {
        this.rejectedBy = rejectedBy;
    }

    public Integer getTotalLeaveTakenLast12Months() {
        return totalLeaveTakenLast12Months != null ? totalLeaveTakenLast12Months : 0;
    }
    public void setTotalLeaveTakenLast12Months(Integer t) { this.totalLeaveTakenLast12Months = t; }

    public String getVerificationToken() {
        return verificationToken;
    }

    public void setVerificationToken(String verificationToken) {
        this.verificationToken = verificationToken;
    }

    public String getEmployeeFullName() {
        if (user != null && user.getPerson() != null) {
            return user.getPerson().getFirstName() + " " + user.getPerson().getLastName();
        }
        return "Unknown";
    }

    public String getEmployeeEmail() {
        if (user != null && user.getPerson() != null) {
            return user.getPerson().getEmail();
        }
        return "Unknown";
    }
}
