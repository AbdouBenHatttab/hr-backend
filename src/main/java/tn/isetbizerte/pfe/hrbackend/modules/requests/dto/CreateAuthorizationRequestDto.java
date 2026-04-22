package tn.isetbizerte.pfe.hrbackend.modules.requests.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tn.isetbizerte.pfe.hrbackend.common.enums.AuthorizationType;

import java.time.LocalDate;

public class CreateAuthorizationRequestDto {

    @NotNull(message = "Authorization type is required")
    @JsonAlias("type")
    private AuthorizationType authorizationType;

    private LocalDate startDate;

    private LocalDate endDate;

    @Size(max = 1000, message = "Reason must not exceed 1000 characters")
    private String reason;

    @AssertTrue(message = "End date must be on or after start date")
    public boolean isDateRangeValid() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }

    public AuthorizationType getAuthorizationType() {
        return authorizationType;
    }

    public void setAuthorizationType(AuthorizationType authorizationType) {
        this.authorizationType = authorizationType;
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
