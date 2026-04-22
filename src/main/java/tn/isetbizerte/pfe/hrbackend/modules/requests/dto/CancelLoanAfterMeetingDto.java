package tn.isetbizerte.pfe.hrbackend.modules.requests.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CancelLoanAfterMeetingDto {

    @NotBlank(message = "Cancellation reason is required.")
    @Size(max = 1000, message = "Cancellation reason must be at most 1000 characters.")
    private String reason;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
