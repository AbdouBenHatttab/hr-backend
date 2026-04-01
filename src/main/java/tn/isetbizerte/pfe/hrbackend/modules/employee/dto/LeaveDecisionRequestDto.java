package tn.isetbizerte.pfe.hrbackend.modules.employee.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class LeaveDecisionRequestDto {

    @NotBlank(message = "Rejection reason is required")
    @Size(max = 2000, message = "Rejection reason must not exceed 2000 characters")
    private String reason;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
