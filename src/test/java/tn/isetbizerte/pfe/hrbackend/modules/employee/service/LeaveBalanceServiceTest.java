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

        assertThat(annualBalance.getReservedDays()).isEqualTo(8);
    }

    @Test
    void reserveConsumeAndRelease_updatesSeparateReservedAndUsedBuckets() {
        service.reserveForRequest(employee, LeaveType.ANNUAL, LocalDate.now(), 5);

        assertThat(annualBalance.getReservedDays()).isEqualTo(5);
        assertThat(annualBalance.getUsedDays()).isZero();
        assertThat(annualBalance.getAvailableDays()).isEqualTo(25);

        LeaveRequest approved = leave(5);
        service.consumeReserved(approved);

        assertThat(annualBalance.getReservedDays()).isZero();
        assertThat(annualBalance.getUsedDays()).isEqualTo(5);
        assertThat(annualBalance.getAvailableDays()).isEqualTo(25);

        service.reserveForRequest(employee, LeaveType.ANNUAL, LocalDate.now(), 4);
        LeaveRequest rejected = leave(4);
        service.releaseReserved(rejected);

        assertThat(annualBalance.getReservedDays()).isZero();
        assertThat(annualBalance.getUsedDays()).isEqualTo(5);
        assertThat(annualBalance.getAvailableDays()).isEqualTo(25);
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
