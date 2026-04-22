package tn.isetbizerte.pfe.hrbackend.modules.employee.entity;

import jakarta.persistence.*;
import tn.isetbizerte.pfe.hrbackend.common.enums.LeaveType;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "employee_leave_balances", uniqueConstraints = {
        @UniqueConstraint(name = "uk_employee_leave_balance_year_type", columnNames = {"user_id", "leave_type", "balance_year"})
})
public class EmployeeLeaveBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "leave_type", nullable = false, length = 40)
    private LeaveType leaveType;

    @Column(name = "balance_year", nullable = false)
    private Integer year;

    @Column(name = "allocated_days", precision = 6, scale = 2)
    private BigDecimal allocatedDays = BigDecimal.ZERO;

    @Column(name = "reserved_days", precision = 6, scale = 2)
    private BigDecimal reservedDays = BigDecimal.ZERO;

    @Column(name = "used_days", precision = 6, scale = 2)
    private BigDecimal usedDays = BigDecimal.ZERO;

    @Column(name = "carry_forward_days", precision = 6, scale = 2)
    private BigDecimal carryForwardDays = BigDecimal.ZERO;

    @Column(name = "last_accrued_month")
    private Integer lastAccruedMonth = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public EmployeeLeaveBalance() {}

    public EmployeeLeaveBalance(User user, LeaveType leaveType, Integer year, Integer allocatedDays) {
        this(user, leaveType, year, BigDecimal.valueOf(allocatedDays != null ? allocatedDays : 0));
    }

    public EmployeeLeaveBalance(User user, LeaveType leaveType, Integer year, BigDecimal allocatedDays) {
        this.user = user;
        this.leaveType = leaveType;
        this.year = year;
        this.allocatedDays = allocatedDays != null ? allocatedDays : BigDecimal.ZERO;
        this.reservedDays = BigDecimal.ZERO;
        this.usedDays = BigDecimal.ZERO;
        this.carryForwardDays = BigDecimal.ZERO;
        this.lastAccruedMonth = 0;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public BigDecimal getAvailableDays() {
        return safe(allocatedDays).subtract(safe(usedDays)).subtract(safe(reservedDays));
    }

    private BigDecimal safe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public LeaveType getLeaveType() { return leaveType; }
    public void setLeaveType(LeaveType leaveType) { this.leaveType = leaveType; }

    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }

    public BigDecimal getAllocatedDays() { return allocatedDays != null ? allocatedDays : BigDecimal.ZERO; }
    public void setAllocatedDays(BigDecimal allocatedDays) { this.allocatedDays = allocatedDays; }
    public void setAllocatedDays(Integer allocatedDays) { this.allocatedDays = BigDecimal.valueOf(allocatedDays != null ? allocatedDays : 0); }

    public BigDecimal getReservedDays() { return reservedDays != null ? reservedDays : BigDecimal.ZERO; }
    public void setReservedDays(BigDecimal reservedDays) { this.reservedDays = reservedDays; }
    public void setReservedDays(Integer reservedDays) { this.reservedDays = BigDecimal.valueOf(reservedDays != null ? reservedDays : 0); }

    public BigDecimal getUsedDays() { return usedDays != null ? usedDays : BigDecimal.ZERO; }
    public void setUsedDays(BigDecimal usedDays) { this.usedDays = usedDays; }
    public void setUsedDays(Integer usedDays) { this.usedDays = BigDecimal.valueOf(usedDays != null ? usedDays : 0); }

    public BigDecimal getCarryForwardDays() { return carryForwardDays != null ? carryForwardDays : BigDecimal.ZERO; }
    public void setCarryForwardDays(BigDecimal carryForwardDays) { this.carryForwardDays = carryForwardDays; }

    public Integer getLastAccruedMonth() { return lastAccruedMonth != null ? lastAccruedMonth : 0; }
    public void setLastAccruedMonth(Integer lastAccruedMonth) { this.lastAccruedMonth = lastAccruedMonth; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
