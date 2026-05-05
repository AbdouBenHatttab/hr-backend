package tn.isetbizerte.pfe.hrbackend.modules.employee.dto;

import tn.isetbizerte.pfe.hrbackend.common.enums.LeaveType;

import java.time.LocalDate;

/**
 * Request DTO for the dry-run leave draft validation endpoint.
 *
 * Intentionally does NOT use @FutureOrPresent or @NotNull bean-validation
 * constraints on dates so that all validation failures are returned as
 * structured errors (valid=false, errors=[...]) rather than HTTP 400
 * responses thrown by the framework before the service layer is reached.
 *
 * The service is responsible for producing clear, structured messages for
 * every rule violation.
 */
public class LeaveDraftValidationRequestDto {

    private LeaveType leaveType;
    private LocalDate startDate;
    private LocalDate endDate;
    private String reason;

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

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
