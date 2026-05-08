package tn.isetbizerte.pfe.hrbackend.modules.requests.dto;

import tn.isetbizerte.pfe.hrbackend.common.enums.LoanType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Response DTO for the dry-run loan draft validation endpoint.
 *
 * Always returned with HTTP 200.
 * {@code valid=true}  — all rules passed; the draft can proceed to real submission.
 * {@code valid=false} — one or more rules failed; {@code errors} lists every violation.
 *
 * No side effects: nothing is saved to DB, no Kafka/outbox events are
 * published, and no history records are created.
 *
 * Optional fields (salary, monthsEmployed, estimatedMonthlyInstallment,
 * systemRecommendation, riskScore, meetingRequired) are populated when
 * the data is available (e.g. person/salary found and scoring ran).
 */
public class ValidateLoanDraftResponseDto {

    /** Whether the draft passes all loan business rules. */
    private boolean valid;

    /** Echoed back from the request. */
    private LoanType loanType;
    private BigDecimal amount;
    private Integer repaymentMonths;
    private String reason;

    /**
     * Human-readable summary suitable for showing in the assistant
     * confirmation dialog when valid=true, or a top-level reason when
     * valid=false and there is a single root cause.
     */
    private String message;

    /** Structured error messages for every rule that failed. */
    private List<String> errors = new ArrayList<>();

    /** Advisory warnings (e.g. scoring REVIEW outcome) — valid may still be true. */
    private List<String> warnings = new ArrayList<>();

    // Context fields populated when available
    private BigDecimal maxEligibleAmount;
    private BigDecimal salary;
    private Long monthsEmployed;
    private BigDecimal estimatedMonthlyInstallment;
    private String systemRecommendation;
    private Integer riskScore;
    private Boolean meetingRequired;

    // -----------------------------------------------------------------------
    // Convenience factory methods
    // -----------------------------------------------------------------------

    public static ValidateLoanDraftResponseDto valid(String message) {
        ValidateLoanDraftResponseDto dto = new ValidateLoanDraftResponseDto();
        dto.valid = true;
        dto.message = message;
        return dto;
    }

    public static ValidateLoanDraftResponseDto invalid(List<String> errors) {
        ValidateLoanDraftResponseDto dto = new ValidateLoanDraftResponseDto();
        dto.valid = false;
        dto.errors = new ArrayList<>(errors);
        return dto;
    }

    public static ValidateLoanDraftResponseDto invalid(String error) {
        ValidateLoanDraftResponseDto dto = new ValidateLoanDraftResponseDto();
        dto.valid = false;
        dto.errors = new ArrayList<>(List.of(error));
        return dto;
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

    public LoanType getLoanType() {
        return loanType;
    }

    public void setLoanType(LoanType loanType) {
        this.loanType = loanType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Integer getRepaymentMonths() {
        return repaymentMonths;
    }

    public void setRepaymentMonths(Integer repaymentMonths) {
        this.repaymentMonths = repaymentMonths;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
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

    public BigDecimal getMaxEligibleAmount() {
        return maxEligibleAmount;
    }

    public void setMaxEligibleAmount(BigDecimal maxEligibleAmount) {
        this.maxEligibleAmount = maxEligibleAmount;
    }

    public BigDecimal getSalary() {
        return salary;
    }

    public void setSalary(BigDecimal salary) {
        this.salary = salary;
    }

    public Long getMonthsEmployed() {
        return monthsEmployed;
    }

    public void setMonthsEmployed(Long monthsEmployed) {
        this.monthsEmployed = monthsEmployed;
    }

    public BigDecimal getEstimatedMonthlyInstallment() {
        return estimatedMonthlyInstallment;
    }

    public void setEstimatedMonthlyInstallment(BigDecimal estimatedMonthlyInstallment) {
        this.estimatedMonthlyInstallment = estimatedMonthlyInstallment;
    }

    public String getSystemRecommendation() {
        return systemRecommendation;
    }

    public void setSystemRecommendation(String systemRecommendation) {
        this.systemRecommendation = systemRecommendation;
    }

    public Integer getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(Integer riskScore) {
        this.riskScore = riskScore;
    }

    public Boolean getMeetingRequired() {
        return meetingRequired;
    }

    public void setMeetingRequired(Boolean meetingRequired) {
        this.meetingRequired = meetingRequired;
    }
}
