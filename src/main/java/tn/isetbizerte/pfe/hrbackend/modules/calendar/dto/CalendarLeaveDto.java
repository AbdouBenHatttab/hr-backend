package tn.isetbizerte.pfe.hrbackend.modules.calendar.dto;

import java.time.LocalDate;

public class CalendarLeaveDto {
    private Long leaveId;
    private LocalDate startDate;
    private LocalDate endDate;
    private Long employeeId;
    private String employeeUsername;
    private String employeeFullName;
    private String status;
    private String leaveType;
    private String reason;
    private Integer numberOfDays;
    private String teamLeaderDecision;
    private String hrDecision;
    private String approvedBy;
    private String rejectedBy;

    public Long getLeaveId() {
        return leaveId;
    }

    public void setLeaveId(Long leaveId) {
        this.leaveId = leaveId;
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

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public String getEmployeeUsername() {
        return employeeUsername;
    }

    public void setEmployeeUsername(String employeeUsername) {
        this.employeeUsername = employeeUsername;
    }

    public String getEmployeeFullName() {
        return employeeFullName;
    }

    public void setEmployeeFullName(String employeeFullName) {
        this.employeeFullName = employeeFullName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLeaveType() {
        return leaveType;
    }

    public void setLeaveType(String leaveType) {
        this.leaveType = leaveType;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Integer getNumberOfDays() {
        return numberOfDays;
    }

    public void setNumberOfDays(Integer numberOfDays) {
        this.numberOfDays = numberOfDays;
    }

    public String getTeamLeaderDecision() {
        return teamLeaderDecision;
    }

    public void setTeamLeaderDecision(String teamLeaderDecision) {
        this.teamLeaderDecision = teamLeaderDecision;
    }

    public String getHrDecision() {
        return hrDecision;
    }

    public void setHrDecision(String hrDecision) {
        this.hrDecision = hrDecision;
    }

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
}
