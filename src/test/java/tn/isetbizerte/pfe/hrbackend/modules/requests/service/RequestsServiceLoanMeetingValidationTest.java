package tn.isetbizerte.pfe.hrbackend.modules.requests.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tn.isetbizerte.pfe.hrbackend.common.enums.LoanType;
import tn.isetbizerte.pfe.hrbackend.common.enums.RequestStatus;
import tn.isetbizerte.pfe.hrbackend.common.event.RequestEvent;
import tn.isetbizerte.pfe.hrbackend.common.exception.BadRequestException;
import tn.isetbizerte.pfe.hrbackend.infrastructure.kafka.producer.RequestEventProducer;
import tn.isetbizerte.pfe.hrbackend.infrastructure.storage.DocumentAttachmentStorageService;
import tn.isetbizerte.pfe.hrbackend.modules.calendar.service.WorkingDayService;
import tn.isetbizerte.pfe.hrbackend.modules.employee.repository.LeaveRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.history.service.RequestHistoryService;
import tn.isetbizerte.pfe.hrbackend.modules.requests.entity.LoanRequest;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.AuthorizationRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.DocumentRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.LoanRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.StoredEmployeeDocumentRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.Person;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.PersonRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.UserRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.service.AuthenticatedUserResolver;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequestsServiceLoanMeetingValidationTest {

    private LoanRequestRepository loanRepo;
    private AuthorizationRequestRepository authRepo;
    private LeaveRequestRepository leaveRequestRepository;
    private WorkingDayService workingDayService;
    private RequestEventProducer requestEventProducer;
    private RequestsService service;

    @BeforeEach
    void setUp() {
        loanRepo = mock(LoanRequestRepository.class);
        authRepo = mock(AuthorizationRequestRepository.class);
        leaveRequestRepository = mock(LeaveRequestRepository.class);
        workingDayService = mock(WorkingDayService.class);
        requestEventProducer = mock(RequestEventProducer.class);

        service = new RequestsService(
                mock(DocumentRequestRepository.class),
                mock(StoredEmployeeDocumentRepository.class),
                loanRepo,
                authRepo,
                mock(UserRepository.class),
                mock(PersonRepository.class),
                mock(AuthenticatedUserResolver.class),
                mock(LoanScoreEngine.class),
                requestEventProducer,
                mock(RequestHistoryService.class),
                mock(DocumentAttachmentStorageService.class),
                leaveRequestRepository,
                workingDayService
        );
    }

    @Test
    void scheduleLoanMeeting_rejectsSameDayFutureSlots() {
        LoanRequest loan = pendingLoan();
        LocalDateTime todayAtValidSlot = LocalDateTime.of(LocalDate.now(), LocalTime.of(16, 0));
        when(loanRepo.findById(loan.getId())).thenReturn(Optional.of(loan));

        assertThatThrownBy(() -> service.scheduleLoanMeeting(loan.getId(), todayAtValidSlot, "", "hr-manager"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Loan meetings must be scheduled at least one day in advance.");

        verify(workingDayService, never()).isWorkingDay(any());
    }

    @Test
    void scheduleLoanMeeting_acceptsFutureWorkingDayValidSlot() {
        LoanRequest loan = pendingLoan();
        LocalDate meetingDate = nextWeekdayAfterToday();
        LocalDateTime meetingAt = LocalDateTime.of(meetingDate, LocalTime.of(8, 0));
        when(loanRepo.findById(loan.getId())).thenReturn(Optional.of(loan));
        when(workingDayService.isWorkingDay(meetingDate)).thenReturn(true);
        when(leaveRequestRepository.findByUserAndDateRangeAndStatusIn(
                eq(loan.getUser()),
                eq(meetingDate),
                eq(meetingDate),
                any()
        )).thenReturn(List.of());

        var result = service.scheduleLoanMeeting(loan.getId(), meetingAt, "Bring payslips", "hr-manager");

        assertThat(result).containsEntry("meetingAt", meetingAt);
        assertThat(loan.getMeetingAt()).isEqualTo(meetingAt);
        verify(loanRepo).save(loan);
        var eventCaptor = forClass(RequestEvent.class);
        verify(requestEventProducer).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getType()).isEqualTo("LOAN_MEETING_SCHEDULED");
    }

    @Test
    void scheduleLoanMeeting_rejectsWhenMeetingAlreadyScheduled() {
        LoanRequest loan = pendingLoan();
        loan.setMeetingAt(LocalDateTime.now().plusDays(2).withHour(8).withMinute(0).withSecond(0).withNano(0));
        LocalDateTime newMeetingAt = LocalDateTime.of(nextWeekdayAfterToday(), LocalTime.of(9, 0));
        when(loanRepo.findById(loan.getId())).thenReturn(Optional.of(loan));

        assertThatThrownBy(() -> service.scheduleLoanMeeting(loan.getId(), newMeetingAt, "New time", "hr-manager"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Loan meeting has already been scheduled and cannot be changed.");

        verify(workingDayService, never()).isWorkingDay(any());
        verify(loanRepo, never()).save(any());
        verify(requestEventProducer, never()).publish(any());
    }

    @Test
    void approveLoan_acceptsHrDefinedFinancialTerms() {
        LoanRequest loan = pendingLoan();
        loan.setMeetingAt(LocalDateTime.now().minusMinutes(30));
        when(loanRepo.findById(loan.getId())).thenReturn(Optional.of(loan));

        var result = service.decideLoan(
                loan.getId(),
                true,
                "Approved with adjusted terms",
                10,
                BigDecimal.valueOf(900),
                BigDecimal.valueOf(90),
                "hr-manager"
        );

        assertThat(loan.getApprovedAmount()).isEqualByComparingTo("900.00");
        assertThat(loan.getRepaymentMonths()).isEqualTo(10);
        assertThat(loan.getMonthlyInstallment()).isEqualByComparingTo("90.00");
        assertThat(result).containsEntry("approvedAmount", BigDecimal.valueOf(900).setScale(2));
        assertThat(result).containsEntry("monthlyPayback", BigDecimal.valueOf(90).setScale(2));
        assertThat(result).containsEntry("totalPayback", BigDecimal.valueOf(900).setScale(2));
        verify(loanRepo).save(loan);
    }

    @Test
    void approveLoan_acceptsPaybackTotalAboveApprovedAmountWithinTwentyPercent() {
        LoanRequest loan = pendingLoan();
        loan.setMeetingAt(LocalDateTime.now().minusMinutes(30));
        when(loanRepo.findById(loan.getId())).thenReturn(Optional.of(loan));

        var result = service.decideLoan(
                loan.getId(),
                true,
                "Approved with additional payback",
                10,
                BigDecimal.valueOf(900),
                BigDecimal.valueOf(105),
                "hr-manager"
        );

        assertThat(loan.getApprovedAmount()).isEqualByComparingTo("900.00");
        assertThat(loan.getRepaymentMonths()).isEqualTo(10);
        assertThat(loan.getMonthlyInstallment()).isEqualByComparingTo("105.00");
        assertThat(result).containsEntry("monthlyPayback", BigDecimal.valueOf(105).setScale(2));
        assertThat(result).containsEntry("totalPayback", BigDecimal.valueOf(1050).setScale(2));
        verify(loanRepo).save(loan);
    }

    @Test
    void approveLoan_acceptsEqualRequestedAmount() {
        LoanRequest loan = pendingLoan();
        loan.setMeetingAt(LocalDateTime.now().minusMinutes(30));
        when(loanRepo.findById(loan.getId())).thenReturn(Optional.of(loan));

        var result = service.decideLoan(
                loan.getId(),
                true,
                "Approved as requested",
                10,
                BigDecimal.valueOf(1000),
                BigDecimal.valueOf(100),
                "hr-manager"
        );

        assertThat(loan.getApprovedAmount()).isEqualByComparingTo("1000.00");
        assertThat(loan.getApprovedAmountJustification()).isNull();
        assertThat(result).containsEntry("approvedAmount", BigDecimal.valueOf(1000).setScale(2));
        assertThat(result).containsEntry("totalPayback", BigDecimal.valueOf(1000).setScale(2));
        verify(loanRepo).save(loan);
    }

    @Test
    void approveLoan_acceptsHigherThanRequestedWithinMaxEligibleWithJustification() {
        LoanRequest loan = pendingLoan();
        loan.setMeetingAt(LocalDateTime.now().minusMinutes(30));
        when(loanRepo.findById(loan.getId())).thenReturn(Optional.of(loan));

        var result = service.decideLoan(
                loan.getId(),
                true,
                "Approved increased amount",
                12,
                BigDecimal.valueOf(1200),
                BigDecimal.valueOf(100),
                "Employee requested a higher amount during the meeting.",
                "hr-manager"
        );

        assertThat(loan.getApprovedAmount()).isEqualByComparingTo("1200.00");
        assertThat(loan.getApprovedAmountJustification()).isEqualTo("Employee requested a higher amount during the meeting.");
        assertThat(result).containsEntry("approvedAmount", BigDecimal.valueOf(1200).setScale(2));
        assertThat(result).containsEntry("approvedAmountJustification", "Employee requested a higher amount during the meeting.");
        verify(loanRepo).save(loan);
    }

    @Test
    void approveLoan_rejectsWhenNoMeetingScheduled() {
        LoanRequest loan = pendingLoan();
        when(loanRepo.findById(loan.getId())).thenReturn(Optional.of(loan));

        assertThatThrownBy(() -> service.decideLoan(
                loan.getId(),
                true,
                "",
                10,
                BigDecimal.valueOf(900),
                BigDecimal.valueOf(90),
                "hr-manager"
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("A meeting must be scheduled before approving this loan.");
    }

    @Test
    void approveLoan_rejectsWhenMeetingIsInFuture() {
        LoanRequest loan = pendingLoan();
        loan.setMeetingAt(LocalDateTime.now().plusMinutes(30));
        when(loanRepo.findById(loan.getId())).thenReturn(Optional.of(loan));

        assertThatThrownBy(() -> service.decideLoan(
                loan.getId(),
                true,
                "",
                10,
                BigDecimal.valueOf(900),
                BigDecimal.valueOf(90),
                "hr-manager"
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("This loan has a scheduled meeting. Final approval is available after the meeting time.");
    }

    @Test
    void approveLoan_rejectsIncreaseWithoutJustification() {
        LoanRequest loan = pendingLoan();
        loan.setMeetingAt(LocalDateTime.now().minusMinutes(30));
        when(loanRepo.findById(loan.getId())).thenReturn(Optional.of(loan));

        assertThatThrownBy(() -> service.decideLoan(
                loan.getId(),
                true,
                "",
                10,
                BigDecimal.valueOf(1100),
                BigDecimal.valueOf(110),
                "hr-manager"
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Justification is required when approving more than the requested amount.");
    }

    @Test
    void approveLoan_rejectsApprovedAmountAboveMaxEligibleAmount() {
        LoanRequest loan = pendingLoan();
        loan.setMeetingAt(LocalDateTime.now().minusMinutes(30));
        when(loanRepo.findById(loan.getId())).thenReturn(Optional.of(loan));

        assertThatThrownBy(() -> service.decideLoan(
                loan.getId(),
                true,
                "",
                10,
                BigDecimal.valueOf(3100),
                BigDecimal.valueOf(310),
                "Approved increase",
                "hr-manager"
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Approved amount cannot exceed the employee's maximum eligible amount.");
    }

    @Test
    void approveLoan_rejectsPaybackTotalBelowApprovedAmount() {
        LoanRequest loan = pendingLoan();
        loan.setMeetingAt(LocalDateTime.now().minusMinutes(30));
        when(loanRepo.findById(loan.getId())).thenReturn(Optional.of(loan));

        assertThatThrownBy(() -> service.decideLoan(
                loan.getId(),
                true,
                "",
                10,
                BigDecimal.valueOf(900),
                BigDecimal.valueOf(80),
                "hr-manager"
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Total payback must cover the approved amount.");
    }

    @Test
    void approveLoan_rejectsPaybackTotalAboveTwentyPercent() {
        LoanRequest loan = pendingLoan();
        loan.setMeetingAt(LocalDateTime.now().minusMinutes(30));
        when(loanRepo.findById(loan.getId())).thenReturn(Optional.of(loan));

        assertThatThrownBy(() -> service.decideLoan(
                loan.getId(),
                true,
                "",
                10,
                BigDecimal.valueOf(900),
                BigDecimal.valueOf(109),
                "hr-manager"
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Total payback cannot exceed 20% above the approved amount.");
    }

    @Test
    void rejectLoan_acceptsWhenNoMeetingScheduled() {
        LoanRequest loan = pendingLoan();
        when(loanRepo.findById(loan.getId())).thenReturn(Optional.of(loan));

        var result = service.decideLoan(
                loan.getId(),
                false,
                "Not eligible for HR approval",
                null,
                null,
                null,
                "hr-manager"
        );

        assertThat(loan.getStatus()).isEqualTo(RequestStatus.REJECTED);
        assertThat(loan.getHrDecisionReason()).isEqualTo("Not eligible for HR approval");
        assertThat(loan.getHrDecisionStage()).isEqualTo("BEFORE_MEETING");
        assertThat(result).containsEntry("status", "REJECTED");
        verify(loanRepo).save(loan);
    }

    @Test
    void rejectLoan_rejectsWhenFutureMeetingScheduled() {
        LoanRequest loan = pendingLoan();
        loan.setMeetingAt(LocalDateTime.now().plusMinutes(30));
        when(loanRepo.findById(loan.getId())).thenReturn(Optional.of(loan));

        assertThatThrownBy(() -> service.decideLoan(
                loan.getId(),
                false,
                "No",
                null,
                null,
                null,
                "hr-manager"
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Loan cannot be rejected after a meeting has been scheduled. Cancel after meeting if no agreement is reached.");

        verify(loanRepo, never()).save(any());
    }

    @Test
    void rejectLoan_rejectsWhenPastMeetingScheduled() {
        LoanRequest loan = pendingLoan();
        loan.setMeetingAt(LocalDateTime.now().minusMinutes(30));
        when(loanRepo.findById(loan.getId())).thenReturn(Optional.of(loan));

        assertThatThrownBy(() -> service.decideLoan(
                loan.getId(),
                false,
                "No",
                null,
                null,
                null,
                "hr-manager"
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Loan cannot be rejected after a meeting has been scheduled. Cancel after meeting if no agreement is reached.");

        verify(loanRepo, never()).save(any());
    }

    @Test
    void cancelLoanAfterMeeting_acceptsWhenMeetingPassed() {
        LoanRequest loan = pendingLoan();
        loan.setMeetingAt(LocalDateTime.now().minusMinutes(30));
        when(loanRepo.findByIdForUpdate(loan.getId())).thenReturn(Optional.of(loan));

        var result = service.cancelLoanAfterMeeting(loan.getId(), "No agreement reached", "hr-manager");

        assertThat(loan.getStatus()).isEqualTo(RequestStatus.CANCELLED_AFTER_MEETING);
        assertThat(loan.getHrDecisionReason()).isEqualTo("No agreement reached");
        assertThat(loan.getHrDecisionStage()).isEqualTo("AFTER_MEETING");
        assertThat(result).containsEntry("status", "CANCELLED_AFTER_MEETING");
        verify(loanRepo).save(loan);
    }

    @Test
    void cancelMyLoanRequest_acceptsWhenNoMeetingScheduled() {
        LoanRequest loan = pendingLoan();
        when(loanRepo.findByIdForUpdate(loan.getId())).thenReturn(Optional.of(loan));

        var result = service.cancelMyLoanRequest(loan.getId(), "employee-kc");

        assertThat(loan.getStatus()).isEqualTo(RequestStatus.CANCELLED_BY_EMPLOYEE);
        assertThat(result).containsEntry("status", "CANCELLED_BY_EMPLOYEE");
        verify(loanRepo).save(loan);
        verify(requestEventProducer).publish(any());
    }

    @Test
    void cancelMyLoanRequest_rejectsWhenMeetingScheduled() {
        LoanRequest loan = pendingLoan();
        loan.setMeetingAt(LocalDateTime.now().plusDays(2));
        when(loanRepo.findByIdForUpdate(loan.getId())).thenReturn(Optional.of(loan));

        assertThatThrownBy(() -> service.cancelMyLoanRequest(loan.getId(), "employee-kc"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Loan cannot be canceled by the employee after a meeting has been scheduled.");

        verify(loanRepo, never()).save(any());
        verify(requestEventProducer, never()).publish(any());
    }

    private LoanRequest pendingLoan() {
        User user = new User();
        user.setId(42L);
        user.setUsername("employee");
        user.setKeycloakId("employee-kc");
        Person person = new Person("Employee", "Example", "employee@example.com");
        person.setSalary(BigDecimal.valueOf(1000));
        user.setPerson(person);

        LoanRequest loan = new LoanRequest();
        loan.setId(100L);
        loan.setUser(user);
        loan.setLoanType(LoanType.PERSONAL_ADVANCE);
        loan.setAmount(BigDecimal.valueOf(1000));
        loan.setRepaymentMonths(6);
        loan.setStatus(RequestStatus.PENDING);
        return loan;
    }

    private LocalDate nextWeekdayAfterToday() {
        LocalDate date = LocalDate.now().plusDays(1);
        while (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            date = date.plusDays(1);
        }
        return date;
    }
}
