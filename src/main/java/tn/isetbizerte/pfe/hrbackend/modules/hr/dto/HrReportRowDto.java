package tn.isetbizerte.pfe.hrbackend.modules.hr.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@NoArgsConstructor
public class HrReportRowDto {

    private Long id;
    private HrReportExportRequest.SourceType sourceType;
    private String requestTypeLabel;
    private String requestSubtype;
    private String status;
    private String stage;
    private HrReportExportRequest.StatusGroup statusGroup;
    private Long employeeId;
    private String employeeName;
    private String username;
    private String email;
    private LocalDate hireDate;
    private Long departmentId;
    private String departmentName;
    private Long teamId;
    private String teamName;
    private String jobTitle;
    private String role;
    private LocalDateTime submittedAt;
    private LocalDateTime decisionAt;
    private LocalDateTime closedAt;
    private Long processingDays;
    private Long pendingAgeDays;
    private String decisionActorId;
    private String decisionActorLabel;
    private String reason;
    private String hrNote;
    private String rejectionReason;
    private String cancellationReason;
    private BigDecimal amount;
    private Integer days;
    private Integer durationMinutes;
    private String leaveType;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer deductedDays;
    private String tlDecision;
    private String hrDecision;
    private String documentType;
    private String fulfillmentStatus;
    private Boolean waitingForHrFile;
    private Boolean readyForDownload;
    private LocalDateTime fileUploadedAt;
    private String loanType;
    private BigDecimal requestedAmount;
    private BigDecimal approvedAmount;
    private Integer repaymentMonths;
    private BigDecimal monthlyInstallment;
    private LocalDateTime meetingAt;
    private String meetingStatus;
    private Integer riskScore;
    private String authorizationType;
    private LocalDate absenceDate;
    private LocalTime fromTime;
    private LocalTime toTime;
    private String equipmentType;
    private String monthKey;
    private String agingBucket;
    private String processingBucket;
    private String departmentGroup;
    private String teamGroup;
    private Boolean isPending;
    private Boolean isApproved;
    private Boolean isRejected;
    private Boolean isCancelled;
    private Boolean isStalePending;
    private Boolean needsHrFile;
    private Object rawSource;

    public Long id() { return id; }
    public HrReportExportRequest.SourceType sourceType() { return sourceType; }
    public String requestTypeLabel() { return requestTypeLabel; }
    public String requestSubtype() { return requestSubtype; }
    public String status() { return status; }
    public String stage() { return stage; }
    public HrReportExportRequest.StatusGroup statusGroup() { return statusGroup; }
    public Long employeeId() { return employeeId; }
    public String employeeName() { return employeeName; }
    public String username() { return username; }
    public String email() { return email; }
    public LocalDate hireDate() { return hireDate; }
    public Long departmentId() { return departmentId; }
    public String departmentName() { return departmentName; }
    public Long teamId() { return teamId; }
    public String teamName() { return teamName; }
    public String jobTitle() { return jobTitle; }
    public String role() { return role; }
    public LocalDateTime submittedAt() { return submittedAt; }
    public LocalDateTime decisionAt() { return decisionAt; }
    public LocalDateTime closedAt() { return closedAt; }
    public Long processingDays() { return processingDays; }
    public Long pendingAgeDays() { return pendingAgeDays; }
    public String decisionActorId() { return decisionActorId; }
    public String decisionActorLabel() { return decisionActorLabel; }
    public String reason() { return reason; }
    public String hrNote() { return hrNote; }
    public String rejectionReason() { return rejectionReason; }
    public String cancellationReason() { return cancellationReason; }
    public BigDecimal amount() { return amount; }
    public Integer days() { return days; }
    public Integer durationMinutes() { return durationMinutes; }
    public String leaveType() { return leaveType; }
    public LocalDate startDate() { return startDate; }
    public LocalDate endDate() { return endDate; }
    public Integer deductedDays() { return deductedDays; }
    public String tlDecision() { return tlDecision; }
    public String hrDecision() { return hrDecision; }
    public String documentType() { return documentType; }
    public String fulfillmentStatus() { return fulfillmentStatus; }
    public Boolean waitingForHrFile() { return waitingForHrFile; }
    public Boolean readyForDownload() { return readyForDownload; }
    public LocalDateTime fileUploadedAt() { return fileUploadedAt; }
    public String loanType() { return loanType; }
    public BigDecimal requestedAmount() { return requestedAmount; }
    public BigDecimal approvedAmount() { return approvedAmount; }
    public Integer repaymentMonths() { return repaymentMonths; }
    public BigDecimal monthlyInstallment() { return monthlyInstallment; }
    public LocalDateTime meetingAt() { return meetingAt; }
    public String meetingStatus() { return meetingStatus; }
    public Integer riskScore() { return riskScore; }
    public String authorizationType() { return authorizationType; }
    public LocalDate absenceDate() { return absenceDate; }
    public LocalTime fromTime() { return fromTime; }
    public LocalTime toTime() { return toTime; }
    public String equipmentType() { return equipmentType; }
    public String monthKey() { return monthKey; }
    public String agingBucket() { return agingBucket; }
    public String processingBucket() { return processingBucket; }
    public String departmentGroup() { return departmentGroup; }
    public String teamGroup() { return teamGroup; }
    public Boolean isPending() { return isPending; }
    public Boolean isApproved() { return isApproved; }
    public Boolean isRejected() { return isRejected; }
    public Boolean isCancelled() { return isCancelled; }
    public Boolean isStalePending() { return isStalePending; }
    public Boolean needsHrFile() { return needsHrFile; }
    public Object rawSource() { return rawSource; }
}
