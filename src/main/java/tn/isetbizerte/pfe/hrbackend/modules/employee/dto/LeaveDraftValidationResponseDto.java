package tn.isetbizerte.pfe.hrbackend.modules.employee.dto;

import tn.isetbizerte.pfe.hrbackend.common.enums.LeaveType;
import tn.isetbizerte.pfe.hrbackend.modules.calendar.dto.WorkingDaysEstimateDto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Response DTO for the dry-run leave draft validation endpoint.
 *
 * Always returned with HTTP 200.
 * {@code valid=true}  — all rules passed; the draft can proceed to real submission.
 * {@code valid=false} — one or more rules failed; {@code errors} lists every violation.
 *
 * No side effects: this object is a pure read result. Nothing is saved,
 * no balance is reserved, no Kafka events are published.
 */
public class LeaveDraftValidationResponseDto {

    /** Whether the draft passes all current leave business rules. */
    private boolean valid;

    /** Echoed back from the request so the client can map it to its state. */
    private LeaveType leaveType;

    /**
     * Number of working days that would be deducted if the request were submitted.
     * Null when dates are invalid.
     */
    private Integer workingDays;

    /**
     * Full breakdown of the date range: excluded weekends and public holidays.
     * Null when dates are invalid.
     */
    private WorkingDaysEstimateDto workingDaysBreakdown;

    /**
     * Current available balance before this request.
     * Null for leave types where balance is not managed (UNPAID, OTHER).
     */
    private BigDecimal balanceBefore;

    /**
     * Projected balance after submission, if balance is managed.
     * Null for non-managed types or when dates are invalid.
     */
    private BigDecimal balanceAfterIfApproved;

    /**
     * Whether the leave type uses a managed balance.
     * False for UNPAID and OTHER.
     */
    private Boolean balanceManaged;

    /**
     * Predicted approval workflow stage on creation:
     * - "PENDING_TL" for EMPLOYEE role (goes to team leader first)
     * - "PENDING_HR"  for TEAM_LEADER role (TL step auto-approved)
     */
    private String initialStage;

    /** Structured error messages for every rule that failed. */
    private List<String> errors = new ArrayList<>();

    /** Non-blocking informational messages (e.g., "balance will reach zero"). */
    private List<String> warnings = new ArrayList<>();

    // -----------------------------------------------------------------------
    // Convenience factory methods
    // -----------------------------------------------------------------------

    public static LeaveDraftValidationResponseDto invalid(List<String> errors) {
        LeaveDraftValidationResponseDto dto = new LeaveDraftValidationResponseDto();
        dto.valid = false;
        dto.errors = new ArrayList<>(errors);
        return dto;
    }

    public static LeaveDraftValidationResponseDto invalid(String singleError) {
        return invalid(List.of(singleError));
    }

    // -----------------------------------------------------------------------
    // Getters and setters
    // -----------------------------------------------------------------------

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public LeaveType getLeaveType() {
        return leaveType;
    }

    public void setLeaveType(LeaveType leaveType) {
        this.leaveType = leaveType;
    }

    public Integer getWorkingDays() {
        return workingDays;
    }

    public void setWorkingDays(Integer workingDays) {
        this.workingDays = workingDays;
    }

    public WorkingDaysEstimateDto getWorkingDaysBreakdown() {
        return workingDaysBreakdown;
    }

    public void setWorkingDaysBreakdown(WorkingDaysEstimateDto workingDaysBreakdown) {
        this.workingDaysBreakdown = workingDaysBreakdown;
    }

    public BigDecimal getBalanceBefore() {
        return balanceBefore;
    }

    public void setBalanceBefore(BigDecimal balanceBefore) {
        this.balanceBefore = balanceBefore;
    }

    public BigDecimal getBalanceAfterIfApproved() {
        return balanceAfterIfApproved;
    }

    public void setBalanceAfterIfApproved(BigDecimal balanceAfterIfApproved) {
        this.balanceAfterIfApproved = balanceAfterIfApproved;
    }

    public Boolean getBalanceManaged() {
        return balanceManaged;
    }

    public void setBalanceManaged(Boolean balanceManaged) {
        this.balanceManaged = balanceManaged;
    }

    public String getInitialStage() {
        return initialStage;
    }

    public void setInitialStage(String initialStage) {
        this.initialStage = initialStage;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors != null ? errors : new ArrayList<>();
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings != null ? warnings : new ArrayList<>();
    }
}
