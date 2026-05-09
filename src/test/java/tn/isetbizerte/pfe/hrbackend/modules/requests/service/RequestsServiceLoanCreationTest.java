package tn.isetbizerte.pfe.hrbackend.modules.requests.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.jwt.Jwt;
import tn.isetbizerte.pfe.hrbackend.common.enums.LoanType;
import tn.isetbizerte.pfe.hrbackend.common.enums.RequestStatus;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RequestsServiceLoanCreationTest {

    private static final LoanType VALID_LOAN_TYPE = LoanType.values()[0];

    private LoanRequestRepository loanRepo;
    private RequestHistoryService historyService;
    private RequestEventProducer requestEventProducer;
    private AuthenticatedUserResolver authenticatedUserResolver;
    private RequestsService service;
    private Jwt jwt;
    private User user;

    @BeforeEach
    void setUp() {
        loanRepo = mock(LoanRequestRepository.class);
        historyService = mock(RequestHistoryService.class);
        requestEventProducer = mock(RequestEventProducer.class);
        authenticatedUserResolver = mock(AuthenticatedUserResolver.class);

        service = new RequestsService(
                mock(DocumentRequestRepository.class),
                mock(StoredEmployeeDocumentRepository.class),
                loanRepo,
                mock(AuthorizationRequestRepository.class),
                mock(UserRepository.class),
                mock(PersonRepository.class),
                authenticatedUserResolver,
                mock(LoanScoreEngine.class),
                requestEventProducer,
                historyService,
                mock(DocumentAttachmentStorageService.class),
                mock(LeaveRequestRepository.class),
                mock(WorkingDayService.class)
        );

        jwt = mock(Jwt.class);
        user = buildUser();

        when(authenticatedUserResolver.require(jwt)).thenReturn(user);
        when(loanRepo.findByUserOrderByRequestedAtDesc(user)).thenReturn(List.of());
    }

    @Test
    void createLoanRequest_existingPendingLoan_shouldRejectAndNotSave() {
        when(loanRepo.findByUserOrderByRequestedAtDesc(user)).thenReturn(List.of(existingLoan(RequestStatus.PENDING)));

        assertThatThrownBy(() -> service.createLoanRequest(jwt, VALID_LOAN_TYPE, new BigDecimal("3000"), 12, "Home renovation"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("pending or active loan");

        verify(loanRepo, never()).save(any());
        verifyNoInteractions(historyService, requestEventProducer);
    }

    @Test
    void createLoanRequest_existingApprovedLoan_shouldRejectAndNotSave() {
        when(loanRepo.findByUserOrderByRequestedAtDesc(user)).thenReturn(List.of(existingLoan(RequestStatus.APPROVED)));

        assertThatThrownBy(() -> service.createLoanRequest(jwt, VALID_LOAN_TYPE, new BigDecimal("3000"), 12, "Home renovation"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("pending or active loan");

        verify(loanRepo, never()).save(any());
        verifyNoInteractions(historyService, requestEventProducer);
    }

    @Test
    void createLoanRequest_existingRejectedLoan_shouldAllowSubmission() {
        when(loanRepo.findByUserOrderByRequestedAtDesc(user)).thenReturn(List.of(existingLoan(RequestStatus.REJECTED)));

        Map<String, Object> result = service.createLoanRequest(jwt, VALID_LOAN_TYPE, new BigDecimal("3000"), 12, "Home renovation");

        ArgumentCaptor<LoanRequest> captor = ArgumentCaptor.forClass(LoanRequest.class);
        verify(loanRepo).save(captor.capture());
        LoanRequest saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(RequestStatus.PENDING);
        assertThat(saved.getSystemRecommendation()).isEqualTo("REVIEW");
        assertThat(result.get("status")).isEqualTo("PENDING");
        assertThat(result.get("systemRecommendation")).isEqualTo("REVIEW");
    }

    @Test
    void createLoanRequest_existingCancelledLoan_shouldAllowSubmission() {
        when(loanRepo.findByUserOrderByRequestedAtDesc(user))
                .thenReturn(List.of(existingLoan(RequestStatus.CANCELLED_BY_EMPLOYEE)));

        Map<String, Object> result = service.createLoanRequest(jwt, VALID_LOAN_TYPE, new BigDecimal("3000"), 12, "Home renovation");

        ArgumentCaptor<LoanRequest> captor = ArgumentCaptor.forClass(LoanRequest.class);
        verify(loanRepo).save(captor.capture());
        LoanRequest saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(RequestStatus.PENDING);
        assertThat(saved.getSystemRecommendation()).isEqualTo("REVIEW");
        assertThat(result.get("status")).isEqualTo("PENDING");
        assertThat(result.get("systemRecommendation")).isEqualTo("REVIEW");
    }

    @Test
    void createLoanRequest_existingCancelledAfterMeetingLoan_shouldAllowSubmission() {
        when(loanRepo.findByUserOrderByRequestedAtDesc(user))
                .thenReturn(List.of(existingLoan(RequestStatus.CANCELLED_AFTER_MEETING)));

        Map<String, Object> result = service.createLoanRequest(jwt, VALID_LOAN_TYPE, new BigDecimal("3000"), 12, "Home renovation");

        ArgumentCaptor<LoanRequest> captor = ArgumentCaptor.forClass(LoanRequest.class);
        verify(loanRepo).save(captor.capture());
        LoanRequest saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(RequestStatus.PENDING);
        assertThat(saved.getSystemRecommendation()).isEqualTo("REVIEW");
        assertThat(result.get("status")).isEqualTo("PENDING");
        assertThat(result.get("systemRecommendation")).isEqualTo("REVIEW");
    }

    @Test
    void createLoanRequest_withoutRepaymentMonths_shouldPersistNull() {
        Map<String, Object> result = service.createLoanRequest(jwt, VALID_LOAN_TYPE, new BigDecimal("3000"), null, "Home renovation");

        ArgumentCaptor<LoanRequest> captor = ArgumentCaptor.forClass(LoanRequest.class);
        verify(loanRepo).save(captor.capture());
        LoanRequest saved = captor.getValue();
        assertThat(saved.getRepaymentMonths()).isNull();
        assertThat(result.get("repaymentMonths")).isNull();
    }

    @Test
    void createLoanRequest_withRepaymentMonths_shouldPersistNull() {
        Map<String, Object> result = service.createLoanRequest(jwt, VALID_LOAN_TYPE, new BigDecimal("3000"), 12, "Home renovation");

        ArgumentCaptor<LoanRequest> captor = ArgumentCaptor.forClass(LoanRequest.class);
        verify(loanRepo).save(captor.capture());
        LoanRequest saved = captor.getValue();
        assertThat(saved.getRepaymentMonths()).isNull();
        assertThat(result.get("repaymentMonths")).isNull();
    }

    private User buildUser() {
        Person person = new Person();
        person.setFirstName("Amina");
        person.setLastName("Ben Ali");
        person.setEmail("amina@example.test");
        person.setSalary(new BigDecimal("3000"));
        person.setHireDate(LocalDate.now().minusMonths(24));
        person.setCurrentMonthlyDeductions(BigDecimal.ZERO);

        User u = new User();
        u.setId(1L);
        u.setUsername("amina");
        u.setKeycloakId("kc-amina");
        u.setPerson(person);
        return u;
    }

    private LoanRequest existingLoan(RequestStatus status) {
        LoanRequest loan = new LoanRequest();
        loan.setStatus(status);
        loan.setUser(user);
        loan.setLoanType(VALID_LOAN_TYPE);
        loan.setAmount(new BigDecimal("1000"));
        loan.setRepaymentMonths(12);
        loan.setReason("Previous request");
        return loan;
    }
}
