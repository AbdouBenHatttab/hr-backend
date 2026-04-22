package tn.isetbizerte.pfe.hrbackend.modules.employee.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tn.isetbizerte.pfe.hrbackend.common.enums.LeaveType;
import tn.isetbizerte.pfe.hrbackend.common.exception.BadRequestException;
import tn.isetbizerte.pfe.hrbackend.modules.employee.entity.EmployeeLeaveBalance;
import tn.isetbizerte.pfe.hrbackend.modules.employee.entity.LeavePolicy;
import tn.isetbizerte.pfe.hrbackend.modules.employee.entity.LeaveRequest;
import tn.isetbizerte.pfe.hrbackend.modules.employee.repository.EmployeeLeaveBalanceRepository;
import tn.isetbizerte.pfe.hrbackend.modules.employee.repository.LeavePolicyRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LeaveBalanceServiceTest {

    private LeavePolicyRepository policyRepository;
    private EmployeeLeaveBalanceRepository balanceRepository;
    private LeaveBalanceService service;
    private User employee;
    private LeavePolicy annualPolicy;
    private EmployeeLeaveBalance annualBalance;

    @BeforeEach
    void setUp() {
        policyRepository = mock(LeavePolicyRepository.class);
        balanceRepository = mock(EmployeeLeaveBalanceRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        service = new LeaveBalanceService(policyRepository, balanceRepository, userRepository);

        employee = new User("kc-employee", "employee");
        employee.setId(10L);
        annualPolicy = new LeavePolicy(LeaveType.ANNUAL, 30, true);
        annualBalance = new EmployeeLeaveBalance(employee, LeaveType.ANNUAL, LocalDate.now().getYear(), 30);

        when(policyRepository.findByLeaveType(LeaveType.ANNUAL)).thenReturn(Optional.of(annualPolicy));
        when(balanceRepository.findByUserAndLeaveTypeAndYear(employee, LeaveType.ANNUAL, LocalDate.now().getYear()))
                .thenReturn(Optional.of(annualBalance));
        when(balanceRepository.save(any(EmployeeLeaveBalance.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void reserveForRequest_rejectsWhenRequestedDaysExceedAvailableBalance() {
        annualBalance.setUsedDays(20);
        annualBalance.setReservedDays(8);

        assertThatThrownBy(() -> service.reserveForRequest(employee, LeaveType.ANNUAL, LocalDate.now(), 3))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Insufficient Annual balance");

        assertThat(annualBalance.getReservedDays()).isEqualByComparingTo("8");
    }

    @Test
    void reserveConsumeAndRelease_updatesSeparateReservedAndUsedBuckets() {
        service.reserveForRequest(employee, LeaveType.ANNUAL, LocalDate.now(), 5);

        assertThat(annualBalance.getReservedDays()).isEqualByComparingTo("5");
        assertThat(annualBalance.getUsedDays()).isEqualByComparingTo("0");
        assertThat(annualBalance.getAvailableDays()).isEqualByComparingTo("25");

        LeaveRequest approved = leave(5);
        service.consumeReserved(approved);

        assertThat(annualBalance.getReservedDays()).isEqualByComparingTo("0");
        assertThat(annualBalance.getUsedDays()).isEqualByComparingTo("5");
        assertThat(annualBalance.getAvailableDays()).isEqualByComparingTo("25");

        service.reserveForRequest(employee, LeaveType.ANNUAL, LocalDate.now(), 4);
        LeaveRequest rejected = leave(4);
        service.releaseReserved(rejected);

        assertThat(annualBalance.getReservedDays()).isEqualByComparingTo("0");
        assertThat(annualBalance.getUsedDays()).isEqualByComparingTo("5");
        assertThat(annualBalance.getAvailableDays()).isEqualByComparingTo("25");
    }

    @Test
    void annualBalanceCreation_appliesCarryForwardCapAndMonthlyAccrual() {
        LeavePolicy policy = new LeavePolicy(LeaveType.ANNUAL, 30, true);
        policy.setAccrualManaged(true);
        policy.setMonthlyAccrualDays(new BigDecimal("2.50"));
        policy.setCarryForwardCapDays(new BigDecimal("5.00"));

        int year = LocalDate.now().getYear();
        EmployeeLeaveBalance previous = new EmployeeLeaveBalance(employee, LeaveType.ANNUAL, year - 1, 30);
        previous.setUsedDays(18);

        when(policyRepository.findByLeaveType(LeaveType.ANNUAL)).thenReturn(Optional.of(policy));
        when(balanceRepository.findByUserAndLeaveTypeAndYear(employee, LeaveType.ANNUAL, year))
                .thenReturn(Optional.empty());
        when(balanceRepository.findByUserAndLeaveTypeAndYear(employee, LeaveType.ANNUAL, year - 1))
                .thenReturn(Optional.of(previous));

        EmployeeLeaveBalance current = service.accrueAnnualLeaveToDate(employee, year);

        BigDecimal expectedAccrual = new BigDecimal("2.50").multiply(BigDecimal.valueOf(LocalDate.now().getMonthValue()));
        assertThat(current.getCarryForwardDays()).isEqualByComparingTo("5");
        assertThat(current.getAllocatedDays()).isEqualByComparingTo(expectedAccrual.add(new BigDecimal("5.00")));
    }

    @Test
    void fixedSpecialLeaveTypes_areManagedButDoNotUseMonthlyAccrual() {
        LeavePolicy sick = new LeavePolicy(LeaveType.SICK, 10, true);
        when(policyRepository.findByLeaveType(LeaveType.SICK)).thenReturn(Optional.of(sick));
        when(balanceRepository.findByUserAndLeaveTypeAndYear(employee, LeaveType.SICK, LocalDate.now().getYear()))
                .thenReturn(Optional.empty());

        service.reserveForRequest(employee, LeaveType.SICK, LocalDate.now(), 2);

        verify(balanceRepository, atLeastOnce()).save(argThat(balance ->
                balance.getLeaveType() == LeaveType.SICK
                        && balance.getAllocatedDays().compareTo(BigDecimal.TEN) == 0
                        && balance.getReservedDays().compareTo(BigDecimal.valueOf(2)) == 0
        ));
    }

    @Test
    void maternityAndPaternity_areFixedSpecialBalances() {
        LeavePolicy maternity = new LeavePolicy(LeaveType.MATERNITY, 60, true);
        LeavePolicy paternity = new LeavePolicy(LeaveType.PATERNITY, 3, true);
        when(policyRepository.findByLeaveType(LeaveType.MATERNITY)).thenReturn(Optional.of(maternity));
        when(policyRepository.findByLeaveType(LeaveType.PATERNITY)).thenReturn(Optional.of(paternity));
        when(balanceRepository.findByUserAndLeaveTypeAndYear(eq(employee), any(), anyInt())).thenReturn(Optional.empty());

        service.reserveForRequest(employee, LeaveType.MATERNITY, LocalDate.now(), 10);
        service.reserveForRequest(employee, LeaveType.PATERNITY, LocalDate.now(), 1);

        verify(balanceRepository, atLeastOnce()).save(argThat(balance ->
                balance.getLeaveType() == LeaveType.MATERNITY
                        && balance.getAllocatedDays().compareTo(BigDecimal.valueOf(60)) == 0
        ));
        verify(balanceRepository, atLeastOnce()).save(argThat(balance ->
                balance.getLeaveType() == LeaveType.PATERNITY
                        && balance.getAllocatedDays().compareTo(BigDecimal.valueOf(3)) == 0
        ));
    }

    @Test
    void unpaidLeave_isNotBalanceManaged() {
        LeavePolicy unpaid = new LeavePolicy(LeaveType.UNPAID, 0, false);
        when(policyRepository.findByLeaveType(LeaveType.UNPAID)).thenReturn(Optional.of(unpaid));

        service.reserveForRequest(employee, LeaveType.UNPAID, LocalDate.now(), 5);

        verify(balanceRepository, never()).findByUserAndLeaveTypeAndYear(any(), eq(LeaveType.UNPAID), anyInt());
    }

    private LeaveRequest leave(int days) {
        LeaveRequest leave = new LeaveRequest();
        leave.setUser(employee);
        leave.setLeaveType(LeaveType.ANNUAL);
        leave.setStartDate(LocalDate.now());
        leave.setEndDate(LocalDate.now().plusDays(days - 1L));
        leave.setNumberOfDays(days);
        return leave;
    }
}
