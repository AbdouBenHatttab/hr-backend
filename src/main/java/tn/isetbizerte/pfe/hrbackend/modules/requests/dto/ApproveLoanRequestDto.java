package tn.isetbizerte.pfe.hrbackend.modules.requests.dto;

import java.math.BigDecimal;

public class ApproveLoanRequestDto {

    private BigDecimal approvedAmount;

    private Integer repaymentMonths;

    private BigDecimal monthlyPayback;

    private BigDecimal monthlyInstallment;

    private String hrNote;

    private String approvedAmountJustification;

    public BigDecimal getApprovedAmount() {
        return approvedAmount;
    }

    public void setApprovedAmount(BigDecimal approvedAmount) {
        this.approvedAmount = approvedAmount;
    }

    public Integer getRepaymentMonths() {
        return repaymentMonths;
    }

    public void setRepaymentMonths(Integer repaymentMonths) {
        this.repaymentMonths = repaymentMonths;
    }

    public BigDecimal getMonthlyPayback() {
        return monthlyPayback;
    }

    public void setMonthlyPayback(BigDecimal monthlyPayback) {
        this.monthlyPayback = monthlyPayback;
    }

    public BigDecimal getMonthlyInstallment() {
        return monthlyInstallment;
    }

    public void setMonthlyInstallment(BigDecimal monthlyInstallment) {
        this.monthlyInstallment = monthlyInstallment;
    }

    public BigDecimal resolveMonthlyPayback() {
        return monthlyPayback != null ? monthlyPayback : monthlyInstallment;
    }

    public String getHrNote() {
        return hrNote;
    }

    public void setHrNote(String hrNote) {
        this.hrNote = hrNote;
    }

    public String getApprovedAmountJustification() {
        return approvedAmountJustification;
    }

    public void setApprovedAmountJustification(String approvedAmountJustification) {
        this.approvedAmountJustification = approvedAmountJustification;
    }
}
