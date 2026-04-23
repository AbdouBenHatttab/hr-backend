package tn.isetbizerte.pfe.hrbackend.modules.requests.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tn.isetbizerte.pfe.hrbackend.common.enums.AuthorizationType;

import java.time.LocalDate;
import java.time.LocalTime;

public class CreateAuthorizationRequestDto {

    @NotNull(message = "Authorization type is required")
    @JsonAlias("type")
    private AuthorizationType authorizationType;

    private LocalDate startDate;

    private LocalDate endDate;

    @JsonAlias("date")
    private LocalDate absenceDate;

    private LocalTime fromTime;

    private LocalTime toTime;

    @Size(max = 1000, message = "Reason must not exceed 1000 characters")
    private String reason;

    @AssertTrue(message = "End date must be on or after start date")
    public boolean isDateRangeValid() {
        if (authorizationType == AuthorizationType.TIME_PERMISSION) return true;
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }

    @AssertTrue(message = "Short absence requests require date, from time, and to time")
    public boolean isShortAbsenceComplete() {
        if (authorizationType != AuthorizationType.TIME_PERMISSION) return true;
        return getEffectiveAbsenceDate() != null && fromTime != null && toTime != null;
    }

    @AssertTrue(message = "To time must be after from time")
    public boolean isShortAbsenceTimeRangeValid() {
        if (authorizationType != AuthorizationType.TIME_PERMISSION || fromTime == null || toTime == null) return true;
        return toTime.isAfter(fromTime);
    }

    public LocalDate getEffectiveAbsenceDate() {
        return absenceDate != null ? absenceDate : startDate;
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

    public LocalDate getAbsenceDate() {
        return absenceDate;
    }

    public void setAbsenceDate(LocalDate absenceDate) {
        this.absenceDate = absenceDate;
    }

    public LocalTime getFromTime() {
        return fromTime;
    }

    public void setFromTime(LocalTime fromTime) {
        this.fromTime = fromTime;
    }

    public LocalTime getToTime() {
        return toTime;
    }

    public void setToTime(LocalTime toTime) {
        this.toTime = toTime;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
