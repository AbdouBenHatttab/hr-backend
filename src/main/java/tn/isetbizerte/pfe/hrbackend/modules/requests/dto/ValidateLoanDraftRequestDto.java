package tn.isetbizerte.pfe.hrbackend.modules.requests.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.*;
import tn.isetbizerte.pfe.hrbackend.common.enums.LoanType;

import java.math.BigDecimal;

/**
 * Request DTO for the dry-run loan draft validation endpoint.
 *
 * repaymentMonths is OPTIONAL here so the assistant can validate the same
 * draft shape as the manual loan form. When omitted, HR confirms the final
 * repayment terms later.
 *
 * Business-rule violations (salary missing, amount too high, etc.) are
 * returned as structured errors (valid=false, errors=[...]) with HTTP 200,
 * not as HTTP 400. @NotNull / @Min / @Max constraints only guard structural
 * field requirements that the framework can enforce before the service layer.
 */
public class ValidateLoanDraftRequestDto {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1.00", message = "Amount must be greater than 0")
    @Digits(integer = 10, fraction = 2, message = "Amount format is invalid")
    private BigDecimal amount;

    @NotNull(message = "Loan type is required")
    @JsonAlias("loanType")
    private LoanType type;

    @NotBlank(message = "Reason is required")
    @Size(max = 1000, message = "Reason must not exceed 1000 characters")
    private String reason;

    /**
     * Optional for assistant preview. If the user did not mention a repayment
     * period, the draft still validates and HR confirms the final terms later.
     */
    @Min(value = 1, message = "Repayment months must be at least 1")
    @Max(value = 120, message = "Repayment months cannot exceed 120")
    private Integer repaymentMonths;

    // --- Getters / Setters ---

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LoanType getType() {
        return type;
    }

    public void setType(LoanType type) {
        this.type = type;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Integer getRepaymentMonths() {
        return repaymentMonths;
    }

    public void setRepaymentMonths(Integer repaymentMonths) {
        this.repaymentMonths = repaymentMonths;
    }
}
