package tn.isetbizerte.pfe.hrbackend.modules.employee.dto;

import tn.isetbizerte.pfe.hrbackend.common.enums.LeaveType;

import java.math.BigDecimal;

public class LeaveBalanceDto {
    private LeaveType leaveType;
    private String leaveTypeLabel;
    private Integer year;
    private Boolean balanceManaged;
    private BigDecimal allocatedDays;
    private BigDecimal reservedDays;
    private BigDecimal usedDays;
    private BigDecimal availableDays;

    public LeaveType getLeaveType() { return leaveType; }
    public void setLeaveType(LeaveType leaveType) { this.leaveType = leaveType; }

    public String getLeaveTypeLabel() { return leaveTypeLabel; }
    public void setLeaveTypeLabel(String leaveTypeLabel) { this.leaveTypeLabel = leaveTypeLabel; }

    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }

    public Boolean getBalanceManaged() { return balanceManaged; }
    public void setBalanceManaged(Boolean balanceManaged) { this.balanceManaged = balanceManaged; }

    public BigDecimal getAllocatedDays() { return allocatedDays; }
    public void setAllocatedDays(BigDecimal allocatedDays) { this.allocatedDays = allocatedDays; }
    public void setAllocatedDays(Integer allocatedDays) { this.allocatedDays = BigDecimal.valueOf(allocatedDays != null ? allocatedDays : 0); }

    public BigDecimal getReservedDays() { return reservedDays; }
    public void setReservedDays(BigDecimal reservedDays) { this.reservedDays = reservedDays; }
    public void setReservedDays(Integer reservedDays) { this.reservedDays = BigDecimal.valueOf(reservedDays != null ? reservedDays : 0); }

    public BigDecimal getUsedDays() { return usedDays; }
    public void setUsedDays(BigDecimal usedDays) { this.usedDays = usedDays; }
    public void setUsedDays(Integer usedDays) { this.usedDays = BigDecimal.valueOf(usedDays != null ? usedDays : 0); }

    public BigDecimal getAvailableDays() { return availableDays; }
    public void setAvailableDays(BigDecimal availableDays) { this.availableDays = availableDays; }
    public void setAvailableDays(Integer availableDays) { this.availableDays = BigDecimal.valueOf(availableDays != null ? availableDays : 0); }
}
