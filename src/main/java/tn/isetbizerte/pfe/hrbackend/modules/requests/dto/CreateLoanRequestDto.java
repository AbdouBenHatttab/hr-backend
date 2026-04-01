package tn.isetbizerte.pfe.hrbackend.modules.requests.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.*;
import tn.isetbizerte.pfe.hrbackend.common.enums.LoanType;

import java.math.BigDecimal;

public class CreateLoanRequestDto {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1.00", message = "Amount must be greater than 0")
    @Digits(integer = 10, fraction = 2, message = "Amount format is invalid")
    private BigDecimal amount;

    @NotNull(message = "Repayment months is required")
    @Min(value = 1, message = "Repayment months must be at least 1")
    @Max(value = 120, message = "Repayment months cannot exceed 120")
    private Integer repaymentMonths;

    @NotNull(message = "Loan type is required")
    @JsonAlias("loanType")
    private LoanType type;

    @NotBlank(message = "Reason is required")
    @Size(max = 1000, message = "Reason must not exceed 1000 characters")
    private String reason;

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
}
