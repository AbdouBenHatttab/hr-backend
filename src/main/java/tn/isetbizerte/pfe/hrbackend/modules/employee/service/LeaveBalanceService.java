package tn.isetbizerte.pfe.hrbackend.modules.employee.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.isetbizerte.pfe.hrbackend.common.enums.LeaveType;
import tn.isetbizerte.pfe.hrbackend.common.exception.BadRequestException;
import tn.isetbizerte.pfe.hrbackend.common.exception.ResourceNotFoundException;
import tn.isetbizerte.pfe.hrbackend.modules.employee.dto.LeaveBalanceDto;
import tn.isetbizerte.pfe.hrbackend.modules.employee.entity.EmployeeLeaveBalance;
import tn.isetbizerte.pfe.hrbackend.modules.employee.entity.LeavePolicy;
import tn.isetbizerte.pfe.hrbackend.modules.employee.entity.LeaveRequest;
import tn.isetbizerte.pfe.hrbackend.modules.employee.repository.EmployeeLeaveBalanceRepository;
import tn.isetbizerte.pfe.hrbackend.modules.employee.repository.LeavePolicyRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;

@Service
public class LeaveBalanceService {
    private static final BigDecimal ANNUAL_MONTHLY_ACCRUAL_DAYS = new BigDecimal("2.50");
    private static final BigDecimal ANNUAL_CARRY_FORWARD_CAP_DAYS = new BigDecimal("5.00");

    private final LeavePolicyRepository leavePolicyRepository;
    private final EmployeeLeaveBalanceRepository balanceRepository;
    private final UserRepository userRepository;

    public LeaveBalanceService(LeavePolicyRepository leavePolicyRepository,
                               EmployeeLeaveBalanceRepository balanceRepository,
                               UserRepository userRepository) {
        this.leavePolicyRepository = leavePolicyRepository;
        this.balanceRepository = balanceRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public boolean isBalanceManaged(LeaveType leaveType) {
        LeavePolicy policy = getOrCreatePolicy(leaveType);
        return Boolean.TRUE.equals(policy.getActive()) && Boolean.TRUE.equals(policy.getBalanceManaged());
    }

    @Transactional
    public void reserveForRequest(User user, LeaveType leaveType, LocalDate startDate, int days) {
        LeavePolicy policy = getOrCreatePolicy(leaveType);
        if (!isManaged(policy)) return;

        EmployeeLeaveBalance balance = getOrCreateBalance(user, policy, startDate.getYear());
        BigDecimal requested = BigDecimal.valueOf(days);
        if (balance.getAvailableDays().compareTo(requested) < 0) {
            throw new BadRequestException(String.format(
                    "Insufficient %s balance for %d day(s). Available: %s, reserved: %s, used: %s.",
                    formatEnum(leaveType.name()),
                    days,
                    formatDays(balance.getAvailableDays()),
                    formatDays(balance.getReservedDays()),
                    formatDays(balance.getUsedDays())
            ));
        }
        balance.setReservedDays(balance.getReservedDays().add(requested));
        balance.setUpdatedAt(LocalDateTime.now());
        balanceRepository.save(balance);
    }

    @Transactional
    public BigDecimal getAvailableDays(User user, LeaveType leaveType, LocalDate startDate) {
        LeavePolicy policy = getOrCreatePolicy(leaveType);
        if (!isManaged(policy)) return BigDecimal.valueOf(Integer.MAX_VALUE);

        EmployeeLeaveBalance balance = getOrCreateBalance(user, policy, startDate.getYear());
        return balance.getAvailableDays();
    }

    @Transactional
    public void consumeReserved(LeaveRequest leave) {
        if (leave == null || leave.getUser() == null || leave.getLeaveType() == null) return;
        LeavePolicy policy = getOrCreatePolicy(leave.getLeaveType());
        if (!isManaged(policy)) return;

        BigDecimal days = BigDecimal.valueOf(leave.getNumberOfDays() != null ? leave.getNumberOfDays() : 0);
        EmployeeLeaveBalance balance = getOrCreateBalance(leave.getUser(), policy, leave.getStartDate().getYear());
        BigDecimal reserved = balance.getReservedDays();

        if (reserved.compareTo(days) < 0) {
            BigDecimal missingDays = days.subtract(reserved);
            BigDecimal freeDays = balance.getAllocatedDays().subtract(balance.getUsedDays()).subtract(reserved);
            if (freeDays.compareTo(missingDays) < 0) {
                throw new BadRequestException(String.format(
                        "Insufficient %s balance to approve this request. Available: %s.",
                        formatEnum(leave.getLeaveType().name()),
                        formatDays(balance.getAvailableDays())
                ));
            }
        }

        balance.setReservedDays(reserved.subtract(days).max(BigDecimal.ZERO));
        balance.setUsedDays(balance.getUsedDays().add(days));
        balance.setUpdatedAt(LocalDateTime.now());
        balanceRepository.save(balance);
    }

    @Transactional
    public void releaseReserved(LeaveRequest leave) {
        if (leave == null || leave.getUser() == null || leave.getLeaveType() == null) return;
        LeavePolicy policy = getOrCreatePolicy(leave.getLeaveType());
        if (!isManaged(policy)) return;

        BigDecimal days = BigDecimal.valueOf(leave.getNumberOfDays() != null ? leave.getNumberOfDays() : 0);
        EmployeeLeaveBalance balance = getOrCreateBalance(leave.getUser(), policy, leave.getStartDate().getYear());
        balance.setReservedDays(balance.getReservedDays().subtract(days).max(BigDecimal.ZERO));
        balance.setUpdatedAt(LocalDateTime.now());
        balanceRepository.save(balance);
    }

    @Transactional
    public List<LeaveBalanceDto> getMyBalances(String userIdentifier, Integer year) {
        User user = resolveUser(userIdentifier);
        int resolvedYear = year != null ? year : LocalDate.now().getYear();
        return Arrays.stream(LeaveType.values())
                .map(type -> toDto(getOrCreatePolicy(type), user, resolvedYear))
                .toList();
    }

    private User resolveUser(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new ResourceNotFoundException("User not found");
        }
        return userRepository.findByKeycloakId(identifier)
                .or(() -> userRepository.findByUsername(identifier))
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + identifier));
    }

    private LeaveBalanceDto toDto(LeavePolicy policy, User user, int year) {
        LeaveBalanceDto dto = new LeaveBalanceDto();
        dto.setLeaveType(policy.getLeaveType());
        dto.setLeaveTypeLabel(formatEnum(policy.getLeaveType().name()));
        dto.setYear(year);
        dto.setBalanceManaged(isManaged(policy));

        if (isManaged(policy)) {
            EmployeeLeaveBalance balance = getOrCreateBalance(user, policy, year);
            dto.setAllocatedDays(balance.getAllocatedDays());
            dto.setReservedDays(balance.getReservedDays());
            dto.setUsedDays(balance.getUsedDays());
            dto.setAvailableDays(balance.getAvailableDays());
        } else {
            dto.setAllocatedDays(policy.getAnnualAllowanceDays());
            dto.setReservedDays(0);
            dto.setUsedDays(0);
            dto.setAvailableDays((BigDecimal) null);
        }
        return dto;
    }

    private EmployeeLeaveBalance getOrCreateBalance(User user, LeavePolicy policy, int year) {
        return balanceRepository.findByUserAndLeaveTypeAndYear(user, policy.getLeaveType(), year)
                .map(balance -> applyAccrualIfNeeded(balance, policy, year))
                .orElseGet(() -> balanceRepository.save(initializeBalance(user, policy, year)));
    }

    private EmployeeLeaveBalance initializeBalance(User user, LeavePolicy policy, int year) {
        EmployeeLeaveBalance balance = new EmployeeLeaveBalance(user, policy.getLeaveType(), year, initialAllocation(policy, user, year));
        if (isAnnualAccrualPolicy(policy)) {
            BigDecimal carryForward = calculateCarryForward(user, year, policy.getCarryForwardCapDays());
            balance.setCarryForwardDays(carryForward);
            balance.setAllocatedDays(carryForward);
            balance.setLastAccruedMonth(0);
            applyAccrualFields(balance, policy, year);
        }
        return balance;
    }

    private EmployeeLeaveBalance applyAccrualIfNeeded(EmployeeLeaveBalance balance, LeavePolicy policy, int year) {
        if (!isAnnualAccrualPolicy(policy)) {
            return balance;
        }

        if (!applyAccrualFields(balance, policy, year)) {
            return balance;
        }
        return balanceRepository.save(balance);
    }

    private boolean applyAccrualFields(EmployeeLeaveBalance balance, LeavePolicy policy, int year) {
        int targetMonth = accrualTargetMonth(year);
        int lastMonth = Math.max(0, Math.min(balance.getLastAccruedMonth(), 12));
        if (targetMonth <= lastMonth) {
            return false;
        }

        BigDecimal monthsToAccrue = BigDecimal.valueOf(targetMonth - lastMonth);
        BigDecimal newAllocation = balance.getAllocatedDays().add(policy.getMonthlyAccrualDays().multiply(monthsToAccrue));
        BigDecimal annualLimit = BigDecimal.valueOf(policy.getAnnualAllowanceDays()).add(balance.getCarryForwardDays());
        balance.setAllocatedDays(newAllocation.min(annualLimit));
        balance.setLastAccruedMonth(targetMonth);
        balance.setUpdatedAt(LocalDateTime.now());
        return true;
    }

    private int accrualTargetMonth(int balanceYear) {
        YearMonth now = YearMonth.now();
        if (balanceYear < now.getYear()) return 12;
        if (balanceYear > now.getYear()) return 0;
        return now.getMonthValue();
    }

    private BigDecimal initialAllocation(LeavePolicy policy, User user, int year) {
        if (isAnnualAccrualPolicy(policy)) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(policy.getAnnualAllowanceDays());
    }

    private BigDecimal calculateCarryForward(User user, int year, BigDecimal cap) {
        if (year <= 0 || cap.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        return balanceRepository.findByUserAndLeaveTypeAndYear(user, LeaveType.ANNUAL, year - 1)
                .map(previous -> previous.getAvailableDays().max(BigDecimal.ZERO).min(cap))
                .orElse(BigDecimal.ZERO);
    }

    @Transactional
    public EmployeeLeaveBalance accrueAnnualLeaveToDate(User user, int year) {
        LeavePolicy policy = getOrCreatePolicy(LeaveType.ANNUAL);
        return getOrCreateBalance(user, policy, year);
    }

    private LeavePolicy getOrCreatePolicy(LeaveType leaveType) {
        return leavePolicyRepository.findByLeaveType(leaveType)
                .orElseGet(() -> leavePolicyRepository.save(defaultPolicy(leaveType)));
    }

    private LeavePolicy defaultPolicy(LeaveType leaveType) {
        return switch (leaveType) {
            case ANNUAL -> {
                LeavePolicy policy = new LeavePolicy(leaveType, 30, true);
                policy.setAccrualManaged(true);
                policy.setMonthlyAccrualDays(ANNUAL_MONTHLY_ACCRUAL_DAYS);
                policy.setCarryForwardCapDays(ANNUAL_CARRY_FORWARD_CAP_DAYS);
                yield policy;
            }
            case SICK -> new LeavePolicy(leaveType, 10, true);
            case MATERNITY -> new LeavePolicy(leaveType, 60, true);
            case PATERNITY -> new LeavePolicy(leaveType, 3, true);
            case UNPAID, OTHER -> new LeavePolicy(leaveType, 0, false);
        };
    }

    private boolean isManaged(LeavePolicy policy) {
        return Boolean.TRUE.equals(policy.getActive()) && Boolean.TRUE.equals(policy.getBalanceManaged());
    }

    private boolean isAnnualAccrualPolicy(LeavePolicy policy) {
        return policy.getLeaveType() == LeaveType.ANNUAL
                && Boolean.TRUE.equals(policy.getAccrualManaged());
    }

    private String formatEnum(String value) {
        String[] words = value.toLowerCase().split("_");
        StringBuilder label = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) continue;
            if (!label.isEmpty()) label.append(' ');
            label.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return label.toString();
    }

    private String formatDays(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
