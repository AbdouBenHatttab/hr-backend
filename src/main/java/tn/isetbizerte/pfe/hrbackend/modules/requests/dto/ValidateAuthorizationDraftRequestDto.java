package tn.isetbizerte.pfe.hrbackend.modules.requests.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tn.isetbizerte.pfe.hrbackend.common.enums.AuthorizationType;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Request DTO for the dry-run authorization draft validation endpoint.
 *
 * Intentionally avoids cross-field @AssertTrue constraints: business-rule
 * violations are collected in the service and returned as structured
 * errors (valid=false, errors=[...]) rather than HTTP 400 responses thrown
 * by the framework before the service layer is reached.
 *
 * Mirrors the fields of CreateAuthorizationRequestDto without @AssertTrue guards.
 */
public class ValidateAuthorizationDraftRequestDto {

    @NotNull(message = "authorizationType is required")
    @JsonAlias("type")
    private AuthorizationType authorizationType;

    // TIME_PERMISSION fields
    @JsonAlias("date")
    private LocalDate absenceDate;

    private LocalTime fromTime;
    private LocalTime toTime;

    // EQUIPMENT_REQUEST fields
    @JsonAlias("neededFrom")
    private LocalDate startDate;

    @JsonAlias("expectedReturnDate")
    private LocalDate endDate;

    private String equipmentType;

    @Size(max = 1000, message = "reason must not exceed 1000 characters")
    private String reason;

    // --- Getters / Setters ---

    public AuthorizationType getAuthorizationType() {
        return authorizationType;
    }

    public void setAuthorizationType(AuthorizationType authorizationType) {
        this.authorizationType = authorizationType;
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

    public String getEquipmentType() {
        return equipmentType;
    }

    public void setEquipmentType(String equipmentType) {
        this.equipmentType = equipmentType;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    /**
     * Mirrors CreateAuthorizationRequestDto: absenceDate takes priority,
     * falls back to startDate for backward compatibility.
     */
    public LocalDate getEffectiveAbsenceDate() {
        return absenceDate != null ? absenceDate : startDate;
    }
}
