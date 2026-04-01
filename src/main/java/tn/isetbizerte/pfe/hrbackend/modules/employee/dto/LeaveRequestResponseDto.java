package tn.isetbizerte.pfe.hrbackend.modules.employee.dto;

import tn.isetbizerte.pfe.hrbackend.common.enums.ApprovalDecision;
import tn.isetbizerte.pfe.hrbackend.common.enums.LeaveStatus;
import tn.isetbizerte.pfe.hrbackend.common.enums.LeaveType;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class LeaveRequestResponseDto {

    private Long id;
    private String employeeFullName;
    private String employeeEmail;
    private LeaveType leaveType;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer numberOfDays;
    private String reason;
    private LocalDateTime requestDate;
    private ApprovalDecision teamLeaderDecision;
    private ApprovalDecision hrDecision;
    private LeaveStatus status;
    private LocalDate approvalDate;
    private LocalDateTime createdAt;

    // Scoring
    private Integer systemScore;
    private String  systemRecommendation;
    private String  decisionReason;
    private String  approvedBy;
    private String  rejectedBy;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmployeeFullName() {
        return employeeFullName;
    }

    public void setEmployeeFullName(String employeeFullName) {
        this.employeeFullName = employeeFullName;
    }

    public String getEmployeeEmail() {
        return employeeEmail;
    }

    public void setEmployeeEmail(String employeeEmail) {
        this.employeeEmail = employeeEmail;
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

    public Integer getSystemScore() { return systemScore; }
    public void setSystemScore(Integer s) { this.systemScore = s; }

    public String getSystemRecommendation() { return systemRecommendation; }
    public void setSystemRecommendation(String r) { this.systemRecommendation = r; }

    public String getDecisionReason() { return decisionReason; }
    public void setDecisionReason(String d) { this.decisionReason = d; }

    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }

    public String getRejectedBy() { return rejectedBy; }
    public void setRejectedBy(String rejectedBy) { this.rejectedBy = rejectedBy; }
}
