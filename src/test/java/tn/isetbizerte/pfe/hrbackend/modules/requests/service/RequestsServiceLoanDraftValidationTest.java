package tn.isetbizerte.pfe.hrbackend.modules.requests.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import tn.isetbizerte.pfe.hrbackend.common.enums.LoanType;
import tn.isetbizerte.pfe.hrbackend.common.enums.RequestStatus;
import tn.isetbizerte.pfe.hrbackend.infrastructure.kafka.producer.RequestEventProducer;
import tn.isetbizerte.pfe.hrbackend.infrastructure.storage.DocumentAttachmentStorageService;
import tn.isetbizerte.pfe.hrbackend.modules.calendar.service.WorkingDayService;
import tn.isetbizerte.pfe.hrbackend.modules.employee.repository.LeaveRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.history.service.RequestHistoryService;
import tn.isetbizerte.pfe.hrbackend.modules.requests.dto.ValidateLoanDraftRequestDto;
import tn.isetbizerte.pfe.hrbackend.modules.requests.dto.ValidateLoanDraftResponseDto;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RequestsService.validateLoanDraft.
 *
 * No Spring context. All collaborators are Mockito mocks.
 * No DB write, no Kafka, no history — pure business-rule coverage.
 */
class RequestsServiceLoanDraftValidationTest {

    private static final LoanType VALID_LOAN_TYPE = LoanType.values()[0];

    private LoanRequestRepository loanRepo;
    private AuthenticatedUserResolver authenticatedUserResolver;
    private LoanScoreEngine loanScoreEngine;
    private RequestHistoryService historyService;
    private RequestEventProducer requestEventProducer;

    private RequestsService service;
    private Jwt jwt;
    private User user;
    private Person person;

    @BeforeEach
    void setUp() {
        loanRepo                 = mock(LoanRequestRepository.class);
        authenticatedUserResolver = mock(AuthenticatedUserResolver.class);
        loanScoreEngine          = mock(LoanScoreEngine.class);
        historyService           = mock(RequestHistoryService.class);
        requestEventProducer     = mock(RequestEventProducer.class);

        service = new RequestsService(
                mock(DocumentRequestRepository.class),
                mock(StoredEmployeeDocumentRepository.class),
                loanRepo,
                mock(AuthorizationRequestRepository.class),
                mock(UserRepository.class),
                mock(PersonRepository.class),
                authenticatedUserResolver,
                loanScoreEngine,
                requestEventProducer,
                historyService,
                mock(DocumentAttachmentStorageService.class),
                mock(LeaveRequestRepository.class),
                mock(WorkingDayService.class)
        );

        jwt    = mock(Jwt.class);
        person = new Person();
        person.setFirstName("Amina");
        person.setLastName("Ben Ali");
        person.setEmail("amina@example.test");
        person.setSalary(new BigDecimal("3000"));
        person.setHireDate(LocalDate.now().minusMonths(24));
        person.setCurrentMonthlyDeductions(BigDecimal.ZERO);

        user = new User();
        user.setId(1L);
        user.setUsername("amina");
        user.setKeycloakId("kc-amina");
        user.setPerson(person);

        when(authenticatedUserResolver.require(jwt)).thenReturn(user);
        when(loanRepo.findByUserOrderByRequestedAtDesc(user)).thenReturn(List.of());

        // Default scoring: APPROVE with riskScore=80, no meetingRequired
        doAnswer(inv -> {
            LoanRequest req = inv.getArgument(0);
            req.setRiskScore(80);
            req.setSystemRecommendation("APPROVE");
            req.setDecisionReason("Loan meets all criteria with acceptable risk.");
            req.setMeetingRequired(false);
            req.setMonthlyInstallment(req.getAmount().divide(BigDecimal.valueOf(req.getRepaymentMonths()), 2, java.math.RoundingMode.HALF_UP));
            return null;
        }).when(loanScoreEngine).evaluate(any(LoanRequest.class));
    }

    // =========================================================================
    // Test 1: valid draft returns valid=true
    // =========================================================================

    @Test
    void validDraft_returnsValidTrue() {
        var req = loanRequest(new BigDecimal("5000"), 12);

        ValidateLoanDraftResponseDto result = service.validateLoanDraft(req, jwt);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getLoanType()).isEqualTo(VALID_LOAN_TYPE);
        assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("5000"));
        assertThat(result.getRepaymentMonths()).isEqualTo(12);
        assertThat(result.getSalary()).isEqualByComparingTo(new BigDecimal("3000"));
        assertThat(result.getMaxEligibleAmount()).isEqualByComparingTo(new BigDecimal("9000"));
        assertThat(result.getSystemRecommendation()).isEqualTo("APPROVE");
        assertThat(result.getRiskScore()).isEqualTo(80);
        assertThat(result.getEstimatedMonthlyInstallment()).isNotNull();
    }

    // =========================================================================
    // Test 2: missing salary returns valid=false
    // =========================================================================

    @Test
    void missingSalary_returnsValidFalse() {
        person.setSalary(null);

        var req = loanRequest(new BigDecimal("2000"), 6);
        var result = service.validateLoanDraft(req, jwt);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.toLowerCase().contains("salary"));
        verifyNoInteractions(loanScoreEngine);
    }

    @Test
    void zeroSalary_returnsValidFalse() {
        person.setSalary(BigDecimal.ZERO);

        var req = loanRequest(new BigDecimal("2000"), 6);
        var result = service.validateLoanDraft(req, jwt);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.toLowerCase().contains("salary"));
        verifyNoInteractions(loanScoreEngine);
    }

    // =========================================================================
    // Test 3: amount above 3x salary returns valid=false
    // =========================================================================

    @Test
    void amountAbove3xSalary_returnsValidFalse() {
        // salary=3000, max=9000, requesting 10000
        var req = loanRequest(new BigDecimal("10000"), 12);

        var result = service.validateLoanDraft(req, jwt);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.toLowerCase().contains("maximum") || e.toLowerCase().contains("exceed"));
        verifyNoInteractions(loanScoreEngine);
    }

    // =========================================================================
    // Test 4: existing PENDING loan returns valid=false
    // =========================================================================

    @Test
    void existingPendingLoan_returnsValidFalse() {
        LoanRequest existing = new LoanRequest();
        existing.setStatus(RequestStatus.PENDING);
        when(loanRepo.findByUserOrderByRequestedAtDesc(user)).thenReturn(List.of(existing));

        var req = loanRequest(new BigDecimal("3000"), 12);
        var result = service.validateLoanDraft(req, jwt);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.toLowerCase().contains("pending") || e.toLowerCase().contains("active"));
        verifyNoInteractions(loanScoreEngine);
    }

    // =========================================================================
    // Test 5: existing APPROVED loan returns valid=false
    // =========================================================================

    @Test
    void existingApprovedLoan_returnsValidFalse() {
        LoanRequest existing = new LoanRequest();
        existing.setStatus(RequestStatus.APPROVED);
        when(loanRepo.findByUserOrderByRequestedAtDesc(user)).thenReturn(List.of(existing));

        var req = loanRequest(new BigDecimal("3000"), 12);
        var result = service.validateLoanDraft(req, jwt);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.toLowerCase().contains("active") || e.toLowerCase().contains("approved"));
        verifyNoInteractions(loanScoreEngine);
    }

    // =========================================================================
    // Test 6: seniority below 6 months returns valid=false
    // =========================================================================

    @Test
    void seniorityBelow6Months_returnsValidFalse() {
        person.setHireDate(LocalDate.now().minusMonths(3));

        var req = loanRequest(new BigDecimal("3000"), 12);
        var result = service.validateLoanDraft(req, jwt);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.toLowerCase().contains("6 months") || e.toLowerCase().contains("seniority") || e.toLowerCase().contains("employed"));
        verifyNoInteractions(loanScoreEngine);
    }

    // =========================================================================
    // Test 7: hireDate null does not fail seniority
    // =========================================================================

    @Test
    void hireDateNull_doesNotFailSeniority() {
        person.setHireDate(null);

        var req = loanRequest(new BigDecimal("3000"), 12);
        var result = service.validateLoanDraft(req, jwt);

        // Should still reach scoring since hireDate=null means seniority check is skipped
        assertThat(result.isValid()).isTrue();
        verify(loanScoreEngine).evaluate(any(LoanRequest.class));
    }

    // =========================================================================
    // Test 8: missing repaymentMonths — DTO @NotNull, so service gets null
    //         The spec says return valid=false with the preview message.
    //         In reality @Valid catches it as HTTP 400 before service, so
    //         this test verifies the DTO constraint is present and meaningful.
    // =========================================================================

    @Test
    void dtoRepaymentMonthsAnnotation_isNotNull() throws Exception {
        var field = ValidateLoanDraftRequestDto.class.getDeclaredField("repaymentMonths");
        field.setAccessible(true);
        var notNull = field.getAnnotation(jakarta.validation.constraints.NotNull.class);
        assertThat(notNull).isNotNull();
        assertThat(notNull.message()).contains("Repayment period is required");
    }

    // =========================================================================
    // Test 9: repaymentMonths outside 1–120 — DTO constraint present
    // =========================================================================

    @Test
    void dtoRepaymentMonthsBounds_enforced() throws Exception {
        var field = ValidateLoanDraftRequestDto.class.getDeclaredField("repaymentMonths");
        field.setAccessible(true);
        var min = field.getAnnotation(jakarta.validation.constraints.Min.class);
        var max = field.getAnnotation(jakarta.validation.constraints.Max.class);
        assertThat(min).isNotNull();
        assertThat(min.value()).isEqualTo(1L);
        assertThat(max).isNotNull();
        assertThat(max.value()).isEqualTo(120L);
    }

    // =========================================================================
    // Test 10: reason required — DTO @NotBlank present
    // =========================================================================

    @Test
    void dtoReasonAnnotation_isNotBlank() throws Exception {
        var field = ValidateLoanDraftRequestDto.class.getDeclaredField("reason");
        field.setAccessible(true);
        var notBlank = field.getAnnotation(jakarta.validation.constraints.NotBlank.class);
        assertThat(notBlank).isNotNull();
    }

    // =========================================================================
    // Test 11: no DB save
    // =========================================================================

    @Test
    void validateLoanDraft_neverCallsLoanRepoSave() {
        var req = loanRequest(new BigDecimal("5000"), 12);

        service.validateLoanDraft(req, jwt);

        verify(loanRepo, never()).save(any());
    }

    // =========================================================================
    // Test 12: no Kafka publish
    // =========================================================================

    @Test
    void validateLoanDraft_neverPublishesKafkaEvent() {
        var req = loanRequest(new BigDecimal("5000"), 12);

        service.validateLoanDraft(req, jwt);

        verifyNoInteractions(requestEventProducer);
    }

    // =========================================================================
    // Test 13: no history record
    // =========================================================================

    @Test
    void validateLoanDraft_neverRecordsHistory() {
        var req = loanRequest(new BigDecimal("5000"), 12);

        service.validateLoanDraft(req, jwt);

        verifyNoInteractions(historyService);
    }

    // =========================================================================
    // Test 14: scoring REVIEW returns valid=true with warning
    // =========================================================================

    @Test
    void scoringReview_returnsValidTrueWithWarning() {
        doAnswer(inv -> {
            LoanRequest r = inv.getArgument(0);
            r.setRiskScore(50);
            r.setSystemRecommendation("REVIEW");
            r.setDecisionReason("HR meeting required before final approval.");
            r.setMeetingRequired(true);
            r.setMonthlyInstallment(r.getAmount().divide(BigDecimal.valueOf(r.getRepaymentMonths()), 2, java.math.RoundingMode.HALF_UP));
            return null;
        }).when(loanScoreEngine).evaluate(any(LoanRequest.class));

        var req = loanRequest(new BigDecimal("8000"), 24);
        var result = service.validateLoanDraft(req, jwt);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getWarnings()).anyMatch(w -> w.toLowerCase().contains("meeting") || w.toLowerCase().contains("review"));
        assertThat(result.getSystemRecommendation()).isEqualTo("REVIEW");
        assertThat(result.getMeetingRequired()).isTrue();
        verify(loanRepo, never()).save(any());
        verifyNoInteractions(requestEventProducer);
        verifyNoInteractions(historyService);
    }

    // =========================================================================
    // Test 15: scoring AUTO-REJECT (deduction threshold) returns valid=false
    // =========================================================================

    @Test
    void scoringAutoReject_returnsValidFalse() {
        doAnswer(inv -> {
            LoanRequest r = inv.getArgument(0);
            r.setRiskScore(20);
            r.setSystemRecommendation("REJECT");
            r.setDecisionReason("AUTO-REJECTED: monthly deductions would exceed 40% of salary.");
            r.setMeetingRequired(false);
            r.setMonthlyInstallment(r.getAmount().divide(BigDecimal.valueOf(r.getRepaymentMonths()), 2, java.math.RoundingMode.HALF_UP));
            return null;
        }).when(loanScoreEngine).evaluate(any(LoanRequest.class));

        var req = loanRequest(new BigDecimal("8000"), 3);
        var result = service.validateLoanDraft(req, jwt);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.toLowerCase().contains("deduction") || e.toLowerCase().contains("40%") || e.toLowerCase().contains("exceed"));
        verify(loanRepo, never()).save(any());
        verifyNoInteractions(requestEventProducer);
        verifyNoInteractions(historyService);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private ValidateLoanDraftRequestDto loanRequest(BigDecimal amount, int repaymentMonths) {
        ValidateLoanDraftRequestDto req = new ValidateLoanDraftRequestDto();
        req.setAmount(amount);
        req.setType(VALID_LOAN_TYPE);
        req.setReason("Home renovation project");
        req.setRepaymentMonths(repaymentMonths);
        return req;
    }
}
