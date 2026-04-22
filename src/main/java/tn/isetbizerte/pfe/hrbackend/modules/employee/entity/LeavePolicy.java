package tn.isetbizerte.pfe.hrbackend.modules.employee.entity;

import jakarta.persistence.*;
import tn.isetbizerte.pfe.hrbackend.common.enums.LeaveType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "leave_policies", uniqueConstraints = {
        @UniqueConstraint(name = "uk_leave_policy_type", columnNames = "leave_type")
})
public class LeavePolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "leave_type", nullable = false, length = 40)
    private LeaveType leaveType;

    @Column(name = "annual_allowance_days", nullable = false)
    private Integer annualAllowanceDays = 0;

    @Column(name = "balance_managed", nullable = false)
    private Boolean balanceManaged = true;

    @Column(name = "accrual_managed")
    private Boolean accrualManaged = false;

    @Column(name = "monthly_accrual_days", precision = 6, scale = 2)
    private BigDecimal monthlyAccrualDays = BigDecimal.ZERO;

    @Column(name = "carry_forward_cap_days", precision = 6, scale = 2)
    private BigDecimal carryForwardCapDays = BigDecimal.ZERO;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public LeavePolicy() {}

    public LeavePolicy(LeaveType leaveType, Integer annualAllowanceDays, Boolean balanceManaged) {
        this.leaveType = leaveType;
        this.annualAllowanceDays = annualAllowanceDays;
        this.balanceManaged = balanceManaged;
        this.active = true;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LeaveType getLeaveType() { return leaveType; }
    public void setLeaveType(LeaveType leaveType) { this.leaveType = leaveType; }

    public Integer getAnnualAllowanceDays() { return annualAllowanceDays; }
    public void setAnnualAllowanceDays(Integer annualAllowanceDays) { this.annualAllowanceDays = annualAllowanceDays; }

    public Boolean getBalanceManaged() { return balanceManaged; }
    public void setBalanceManaged(Boolean balanceManaged) { this.balanceManaged = balanceManaged; }

    public Boolean getAccrualManaged() { return accrualManaged; }
    public void setAccrualManaged(Boolean accrualManaged) { this.accrualManaged = accrualManaged; }

    public BigDecimal getMonthlyAccrualDays() { return monthlyAccrualDays != null ? monthlyAccrualDays : BigDecimal.ZERO; }
    public void setMonthlyAccrualDays(BigDecimal monthlyAccrualDays) { this.monthlyAccrualDays = monthlyAccrualDays; }

    public BigDecimal getCarryForwardCapDays() { return carryForwardCapDays != null ? carryForwardCapDays : BigDecimal.ZERO; }
    public void setCarryForwardCapDays(BigDecimal carryForwardCapDays) { this.carryForwardCapDays = carryForwardCapDays; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
