package tn.isetbizerte.pfe.hrbackend.modules.employee.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.isetbizerte.pfe.hrbackend.common.enums.LeaveStatus;
import tn.isetbizerte.pfe.hrbackend.common.enums.LeaveType;
import tn.isetbizerte.pfe.hrbackend.common.exception.BadRequestException;
import tn.isetbizerte.pfe.hrbackend.infrastructure.email.HREmailService;
import tn.isetbizerte.pfe.hrbackend.infrastructure.kafka.producer.RequestEventProducer;
import tn.isetbizerte.pfe.hrbackend.modules.employee.dto.CreateLeaveRequestDto;
import tn.isetbizerte.pfe.hrbackend.modules.employee.entity.LeaveRequest;
import tn.isetbizerte.pfe.hrbackend.modules.employee.repository.LeaveRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.history.service.RequestHistoryService;
import tn.isetbizerte.pfe.hrbackend.modules.team.repository.TeamRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.PersonRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.UserRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeLeaveServiceCriticalRulesTest {

    @Mock private LeaveRequestRepository leaveRequestRepository;
    @Mock private UserRepository userRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private PersonRepository personRepository;
    @Mock private LeaveScoreEngine leaveScoreEngine;
    @Mock private HREmailService emailService;
    @Mock private RequestEventProducer requestEventProducer;
    @Mock private RequestHistoryService historyService;
    @Mock private LeaveBalanceService leaveBalanceService;

    private EmployeeLeaveService service;
    private User employee;

    @BeforeEach
    void setUp() {
        service = new EmployeeLeaveService(
                leaveRequestRepository,
                userRepository,
                teamRepository,
                personRepository,
                leaveScoreEngine,
                emailService,
                requestEventProducer,
                historyService,
                leaveBalanceService
        );
        employee = new User("kc-employee", "employee");
        employee.setId(10L);
        employee.setActive(true);
        when(userRepository.findByUsername("employee")).thenReturn(Optional.of(employee));
    }

    @Test
    void createLeaveRequest_rejectsOverlappingPendingOrApprovedLeave() {
        LocalDate start = LocalDate.now().plusDays(10);
        LeaveRequest existing = new LeaveRequest();
        existing.setStatus(LeaveStatus.PENDING);
        existing.setStartDate(start.minusDays(1));
        existing.setEndDate(start.plusDays(1));

        when(leaveRequestRepository.findByUserAndDateRangeAndStatusIn(
                eq(employee), eq(start), eq(start), anyList()
        )).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.createLeaveRequest("employee", dto(LeaveType.SICK, start, 1)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("This leave request overlaps with an existing pending leave request from %s to %s.",
                        existing.getStartDate(), existing.getEndDate());

        verifyNoInteractions(leaveBalanceService);
        verify(leaveRequestRepository, never()).save(any());
    }

    @Test
    void createLeaveRequest_rejectsAnnualLeaveMoreThanSixMonthsInAdvance() {
        LocalDate start = LocalDate.now().plusMonths(6).plusDays(1);
        when(leaveRequestRepository.findByUserAndDateRangeAndStatusIn(
                eq(employee), eq(start), eq(start), anyList()
        )).thenReturn(List.of());

        assertThatThrownBy(() -> service.createLeaveRequest("employee", dto(LeaveType.ANNUAL, start, 1)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Annual leave cannot be requested more than 6 months in advance.");

        verify(leaveBalanceService, never()).reserveForRequest(any(), any(), any(), anyInt());
        verify(leaveRequestRepository, never()).save(any());
    }

    @Test
    void createLeaveRequest_rejectsAnnualLeaveWhenRequestedDaysExceedAvailableBalance() {
        LocalDate start = LocalDate.now().plusDays(20);
        when(leaveRequestRepository.findByUserAndDateRangeAndStatusIn(
                eq(employee), eq(start), eq(start.plusDays(4)), anyList()
        )).thenReturn(List.of());
        when(leaveBalanceService.getAvailableDays(employee, LeaveType.ANNUAL, start)).thenReturn(3);

        assertThatThrownBy(() -> service.createLeaveRequest("employee", dto(LeaveType.ANNUAL, start, 5)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Insufficient annual leave balance. Requested 5 days, but only 3 days are available.");

        verify(leaveBalanceService, never()).reserveForRequest(any(), any(), any(), anyInt());
        verify(leaveRequestRepository, never()).save(any());
    }

    private CreateLeaveRequestDto dto(LeaveType type, LocalDate start, int days) {
        CreateLeaveRequestDto dto = new CreateLeaveRequestDto();
        dto.setLeaveType(type);
        dto.setStartDate(start);
        dto.setEndDate(start.plusDays(days - 1L));
        dto.setNumberOfDays(days);
        dto.setReason("Family need");
        return dto;
    }
}
