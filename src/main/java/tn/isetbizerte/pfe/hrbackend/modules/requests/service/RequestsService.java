package tn.isetbizerte.pfe.hrbackend.modules.requests.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import tn.isetbizerte.pfe.hrbackend.common.enums.*;
import tn.isetbizerte.pfe.hrbackend.common.exception.BadRequestException;
import tn.isetbizerte.pfe.hrbackend.common.exception.ResourceNotFoundException;
import tn.isetbizerte.pfe.hrbackend.common.exception.UnauthorizedException;
import tn.isetbizerte.pfe.hrbackend.common.event.RequestEvent;
import tn.isetbizerte.pfe.hrbackend.infrastructure.kafka.producer.RequestEventProducer;
import tn.isetbizerte.pfe.hrbackend.modules.history.service.RequestHistoryService;
import tn.isetbizerte.pfe.hrbackend.modules.requests.entity.*;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.*;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.UserRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.PersonRepository;
import tn.isetbizerte.pfe.hrbackend.infrastructure.email.HREmailService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
@Slf4j
public class RequestsService {

    private final DocumentRequestRepository      documentRepo;
    private final LoanRequestRepository          loanRepo;
    private final AuthorizationRequestRepository authRepo;
    private final UserRepository                 userRepository;
    private final PersonRepository               personRepository;
    private final LoanScoreEngine                loanScoreEngine;
    private final HREmailService                 emailService;
    private final RequestEventProducer           requestEventProducer;
    private final RequestHistoryService          historyService;

    public RequestsService(DocumentRequestRepository documentRepo,
                           LoanRequestRepository loanRepo,
                           AuthorizationRequestRepository authRepo,
                           UserRepository userRepository,
                           PersonRepository personRepository,
                           LoanScoreEngine loanScoreEngine,
                           HREmailService emailService,
                           RequestEventProducer requestEventProducer,
                           RequestHistoryService historyService) {
        this.documentRepo     = documentRepo;
        this.loanRepo         = loanRepo;
        this.authRepo         = authRepo;
        this.userRepository   = userRepository;
        this.personRepository = personRepository;
        this.loanScoreEngine  = loanScoreEngine;
        this.emailService     = emailService;
        this.requestEventProducer  = requestEventProducer;
        this.historyService   = historyService;
    }

    // ─────────────────────────────────────────────────────────────
    // DOCUMENT REQUESTS
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> createDocumentRequest(String username, String documentType, String notes) {
        User user = getUser(username);
        DocumentRequest req = new DocumentRequest();
        req.setUser(user);
        req.setDocumentType(DocumentType.valueOf(documentType));
        req.setNotes(notes);
        documentRepo.save(req);
        historyService.record(
                "DOCUMENT",
                "CREATED",
                req.getId(),
                req.getUser().getKeycloakId(),
                req.getNotes()
        );
        return mapDocument(req);
    }

    public Page<Map<String, Object>> getMyDocumentRequests(String username, Pageable pageable) {
        User user = getUser(username);
        return documentRepo.findByUserOrderByRequestedAtDesc(user, pageable)
                .map(this::mapDocument);
    }

    public Page<Map<String, Object>> getAllDocumentRequests(Pageable pageable) {
        return documentRepo.findAllByOrderByRequestedAtDesc(pageable)
                .map(this::mapDocument);
    }

    @Transactional
    public Map<String, Object> decideDocument(Long id, boolean approve, String hrNote, String decidedByKeycloakId) {
        DocumentRequest req = documentRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document request not found: " + id));
        if (req.getStatus() != RequestStatus.PENDING)
            throw new BadRequestException("Request already " + req.getStatus().name().toLowerCase());
        req.setStatus(approve ? RequestStatus.APPROVED : RequestStatus.REJECTED);
        req.setHrNote(hrNote);
        if (approve) req.setApprovedBy(decidedByKeycloakId);
        else req.setRejectedBy(decidedByKeycloakId);
        req.setProcessedAt(LocalDateTime.now());
        if (approve) req.setVerificationToken(UUID.randomUUID().toString());
        documentRepo.save(req);
        log.info("Document {} by HR. requestId={} actorId={}",
                approve ? "approved" : "rejected", req.getId(), decidedByKeycloakId);
        historyService.record(
                "DOCUMENT",
                approve ? "HR_APPROVED" : "HR_REJECTED",
                req.getId(),
                decidedByKeycloakId,
                hrNote
        );
        requestEventProducer.publish(
                new RequestEvent(
                        approve ? "DOCUMENT_APPROVED" : "DOCUMENT_REJECTED",
                        "DOCUMENT",
                        req.getId(),
                        req.getUser().getKeycloakId(),
                        decidedByKeycloakId,
                        hrNote
                )
        );
        return mapDocument(req);
    }

    // ─────────────────────────────────────────────────────────────
    // LOAN REQUESTS
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> createLoanRequest(String username, LoanType loanType,
                                                  BigDecimal amount, int repaymentMonths, String reason) {
        User user = getUser(username);
        validateLoanEligibility(user, amount);

        LoanRequest req = new LoanRequest();
        req.setUser(user);
        req.setLoanType(loanType);
        req.setAmount(amount);
        req.setRepaymentMonths(repaymentMonths);
        req.setReason(reason);

        // Run scoring engine — sets monthlyInstallment, riskScore, recommendation, decisionReason
        loanScoreEngine.evaluate(req);

        // If system auto-rejects (deductions > 40%), block immediately
        if ("REJECT".equals(req.getSystemRecommendation())
                && req.getDecisionReason() != null
                && req.getDecisionReason().contains("AUTO-REJECTED")) {
            throw new BadRequestException(req.getDecisionReason());
        }

        loanRepo.save(req);
        historyService.record(
                "LOAN",
                "CREATED",
                req.getId(),
                req.getUser().getKeycloakId(),
                req.getReason()
        );
        return mapLoan(req);
    }

    /**
     * Loan eligibility rules — objective, cannot be bypassed:
     * 1. Salary must be set by HR before any loan can be requested
     * 2. Max loan = 3 × monthly salary
     * 3. No existing PENDING or active (APPROVED but unfinished) loan
     * 4. Must have been hired at least 6 months ago
     */
    private void validateLoanEligibility(User user, BigDecimal amount) {
        if (user.getPerson() == null)
            throw new BadRequestException("Your profile is incomplete. Contact HR.");

        // Rule 1 — salary must exist
        BigDecimal salary = user.getPerson().getSalary();
        if (salary == null || salary.compareTo(BigDecimal.ZERO) <= 0)
            throw new BadRequestException(
                "Your salary has not been registered in the system yet. " +
                "Please contact HR to set up your employment details before requesting a loan.");

        // Rule 2 — max 3x monthly salary
        BigDecimal maxAllowed = salary.multiply(new BigDecimal("3"));
        if (amount.compareTo(maxAllowed) > 0)
            throw new BadRequestException(String.format(
                "Requested amount (%.0f TND) exceeds the maximum allowed (3× salary = %.0f TND).",
                amount, maxAllowed));

        // Rule 3 — no existing pending/approved loan
        boolean hasActiveLoan = loanRepo.findByUserOrderByRequestedAtDesc(user).stream()
                .anyMatch(l -> l.getStatus() == RequestStatus.PENDING ||
                               l.getStatus() == RequestStatus.APPROVED);
        if (hasActiveLoan)
            throw new BadRequestException(
                "You already have a pending or active loan. " +
                "You must fully repay your current loan before requesting a new one.");

        // Rule 4 — minimum 6 months employment
        if (user.getPerson().getHireDate() != null) {
            long monthsEmployed = java.time.temporal.ChronoUnit.MONTHS.between(
                    user.getPerson().getHireDate(), java.time.LocalDate.now());
            if (monthsEmployed < 6)
                throw new BadRequestException(String.format(
                    "You must be employed for at least 6 months before requesting a loan. " +
                    "You have been employed for %d month(s).", monthsEmployed));
        }
    }

    /**
     * Returns the employee's loan eligibility details without submitting anything.
     * Frontend uses this to show the eligibility panel before the form.
     */
    public Map<String, Object> getLoanEligibility(String username) {
        User user = getUser(username);
        Map<String, Object> result = new HashMap<>();

        if (user.getPerson() == null || user.getPerson().getSalary() == null) {
            result.put("eligible", false);
            result.put("reason", "Salary not set. Contact HR to register your employment details.");
            result.put("salary", null);
            result.put("maxLoan", null);
            result.put("monthsEmployed", null);
            result.put("hasActiveLoan", false);
            return result;
        }

        BigDecimal salary = user.getPerson().getSalary();
        BigDecimal maxLoan = salary.multiply(new BigDecimal("3"));

        boolean hasActiveLoan = loanRepo.findByUserOrderByRequestedAtDesc(user).stream()
                .anyMatch(l -> l.getStatus() == RequestStatus.PENDING ||
                               l.getStatus() == RequestStatus.APPROVED);

        long monthsEmployed = 0;
        if (user.getPerson().getHireDate() != null)
            monthsEmployed = java.time.temporal.ChronoUnit.MONTHS.between(
                    user.getPerson().getHireDate(), java.time.LocalDate.now());

        boolean seniorityOk = user.getPerson().getHireDate() == null || monthsEmployed >= 6;
        boolean eligible = !hasActiveLoan && seniorityOk;

        String reason = null;
        if (hasActiveLoan) reason = "You already have an active or pending loan.";
        else if (!seniorityOk) reason = String.format("Need %d more month(s) of employment.", 6 - monthsEmployed);

        result.put("eligible",      eligible);
        result.put("reason",        reason);
        result.put("salary",        salary);
        result.put("maxLoan",       maxLoan);
        result.put("monthsEmployed",monthsEmployed);
        result.put("hasActiveLoan", hasActiveLoan);
        result.put("hireDate",      user.getPerson().getHireDate());
        return result;
    }

    public Page<Map<String, Object>> getMyLoanRequests(String username, Pageable pageable) {
        User user = getUser(username);
        return loanRepo.findByUserOrderByRequestedAtDesc(user, pageable)
                .map(this::mapLoan);
    }

    public Page<Map<String, Object>> getAllLoanRequests(Pageable pageable) {
        return loanRepo.findAllByOrderByRequestedAtDesc(pageable)
                .map(this::mapLoan);
    }

    @Transactional
    public Map<String, Object> decideLoan(Long id, boolean approve, String hrNote, String decidedByKeycloakId) {
        LoanRequest req = loanRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Loan request not found: " + id));
        if (req.getStatus() != RequestStatus.PENDING)
            throw new BadRequestException("Request already " + req.getStatus().name().toLowerCase());
        req.setStatus(approve ? RequestStatus.APPROVED : RequestStatus.REJECTED);
        req.setHrNote(hrNote);
        if (approve) req.setApprovedBy(decidedByKeycloakId);
        else req.setRejectedBy(decidedByKeycloakId);
        req.setProcessedAt(LocalDateTime.now());
        if (approve) {
            req.setVerificationToken(UUID.randomUUID().toString());
            // Update monthly deductions
            if (req.getMonthlyInstallment() != null
                    && req.getUser() != null
                    && req.getUser().getPerson() != null) {
                var person = req.getUser().getPerson();
                BigDecimal current = person.getCurrentMonthlyDeductions();
                person.setCurrentMonthlyDeductions(current.add(req.getMonthlyInstallment()));
                personRepository.save(person);
            }
        }
        loanRepo.save(req);
        log.info("Loan {} by HR. requestId={} actorId={}",
                approve ? "approved" : "rejected", req.getId(), decidedByKeycloakId);
        historyService.record(
                "LOAN",
                approve ? "HR_APPROVED" : "HR_REJECTED",
                req.getId(),
                decidedByKeycloakId,
                hrNote
        );
        requestEventProducer.publish(
                new RequestEvent(
                        approve ? "LOAN_APPROVED" : "LOAN_REJECTED",
                        "LOAN",
                        req.getId(),
                        req.getUser().getKeycloakId(),
                        decidedByKeycloakId,
                        hrNote
                )
        );
        // Send email notification
        sendLoanDecisionEmail(req, approve);
        return mapLoan(req);
    }

    private void sendLoanDecisionEmail(LoanRequest req, boolean approved) {
        try {
            if (req.getUser() == null || req.getUser().getPerson() == null) return;
            var p = req.getUser().getPerson();
            String email = p.getEmail();
            if (email == null || email.isBlank()) return;
            String refId = "LOAN-" + String.format("%06d", req.getId());
            double amount = req.getAmount().doubleValue();
            if (approved) {
                double installment = req.getMonthlyInstallment() != null
                        ? req.getMonthlyInstallment().doubleValue() : 0;
                emailService.sendLoanApproved(email, p.getFirstName(), p.getLastName(),
                        amount, req.getRepaymentMonths(), installment, refId);
            } else {
                String reason = req.getHrNote() != null && !req.getHrNote().isBlank()
                        ? req.getHrNote() : req.getDecisionReason();
                emailService.sendLoanRejected(email, p.getFirstName(), p.getLastName(),
                        amount, reason, refId);
            }
        } catch (Exception e) {
            // Email failure must never break the main flow
            log.error("Failed to send loan decision email. requestId={}", req.getId(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // AUTHORIZATION REQUESTS
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> createAuthRequest(String username, String authType,
                                                  LocalDate startDate, LocalDate endDate, String reason) {
        User user = getUser(username);
        AuthorizationRequest req = new AuthorizationRequest();
        req.setUser(user);
        req.setAuthorizationType(AuthorizationType.valueOf(authType));
        req.setStartDate(startDate);
        req.setEndDate(endDate);
        req.setReason(reason);
        authRepo.save(req);
        historyService.record(
                "AUTH",
                "CREATED",
                req.getId(),
                req.getUser().getKeycloakId(),
                req.getReason()
        );
        return mapAuth(req);
    }

    public Page<Map<String, Object>> getMyAuthRequests(String username, Pageable pageable) {
        User user = getUser(username);
        return authRepo.findByUserOrderByRequestedAtDesc(user, pageable)
                .map(this::mapAuth);
    }

    public Page<Map<String, Object>> getAllAuthRequests(Pageable pageable) {
        return authRepo.findAllByOrderByRequestedAtDesc(pageable)
                .map(this::mapAuth);
    }

    @Transactional
    public Map<String, Object> decideAuth(Long id, boolean approve, String hrNote, String decidedByKeycloakId) {
        AuthorizationRequest req = authRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Authorization request not found: " + id));
        if (req.getStatus() != RequestStatus.PENDING)
            throw new BadRequestException("Request already " + req.getStatus().name().toLowerCase());
        req.setStatus(approve ? RequestStatus.APPROVED : RequestStatus.REJECTED);
        req.setHrNote(hrNote);
        if (approve) req.setApprovedBy(decidedByKeycloakId);
        else req.setRejectedBy(decidedByKeycloakId);
        req.setProcessedAt(LocalDateTime.now());
        if (approve) req.setVerificationToken(UUID.randomUUID().toString());
        authRepo.save(req);
        log.info("Authorization {} by HR. requestId={} actorId={}",
                approve ? "approved" : "rejected", req.getId(), decidedByKeycloakId);
        historyService.record(
                "AUTH",
                approve ? "HR_APPROVED" : "HR_REJECTED",
                req.getId(),
                decidedByKeycloakId,
                hrNote
        );
        requestEventProducer.publish(
                new RequestEvent(
                        approve ? "AUTH_APPROVED" : "AUTH_REJECTED",
                        "AUTH",
                        req.getId(),
                        req.getUser().getKeycloakId(),
                        decidedByKeycloakId,
                        hrNote
                )
        );
        return mapAuth(req);
    }

    // ─────────────────────────────────────────────────────────────
    // PDF GETTERS — used by report controller
    // ─────────────────────────────────────────────────────────────

    public DocumentRequest getDocumentRequestForPdf(Long id, String requesterKeycloakId) {
        DocumentRequest req = documentRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document request not found: " + id));
        if (req.getStatus() != RequestStatus.APPROVED)
            throw new BadRequestException("PDF only available for approved requests.");
        validateOwnerOrHR(req.getUser().getKeycloakId(), requesterKeycloakId);
        return req;
    }

    public LoanRequest getLoanRequestForPdf(Long id, String requesterKeycloakId) {
        LoanRequest req = loanRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Loan request not found: " + id));
        if (req.getStatus() != RequestStatus.APPROVED)
            throw new BadRequestException("PDF only available for approved requests.");
        validateOwnerOrHR(req.getUser().getKeycloakId(), requesterKeycloakId);
        return req;
    }

    public AuthorizationRequest getAuthRequestForPdf(Long id, String requesterKeycloakId) {
        AuthorizationRequest req = authRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Authorization request not found: " + id));
        if (req.getStatus() != RequestStatus.APPROVED)
            throw new BadRequestException("PDF only available for approved requests.");
        validateOwnerOrHR(req.getUser().getKeycloakId(), requesterKeycloakId);
        return req;
    }

    // ─────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────

    private void validateOwnerOrHR(String ownerKeycloakId, String requesterKeycloakId) {
        User requester = userRepository.findByKeycloakId(requesterKeycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("Requester user not found"));
        boolean isHR = requester.getRole() == TypeRole.HR_MANAGER;
        if (!isHR && !ownerKeycloakId.equals(requesterKeycloakId)) {
            throw new AccessDeniedException("You can only download your own documents.");
        }
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }

    private String fmt(String enumName) {
        if (enumName == null) return "";
        return Arrays.stream(enumName.split("_"))
                .map(w -> w.charAt(0) + w.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }

    private Map<String, Object> mapDocument(DocumentRequest r) {
        Map<String, Object> m = new HashMap<>();
        m.put("id",           r.getId());
        m.put("documentType", r.getDocumentType().name());
        m.put("documentTypeLabel", fmt(r.getDocumentType().name()));
        m.put("notes",        r.getNotes());
        m.put("status",       r.getStatus().name());
        m.put("approvedBy",   r.getApprovedBy());
        m.put("rejectedBy",   r.getRejectedBy());
        m.put("hrNote",       r.getHrNote());
        m.put("requestedAt",  r.getRequestedAt());
        m.put("processedAt",  r.getProcessedAt());
        m.put("hasDocument",  r.getVerificationToken() != null);
        m.put("employeeName", r.getEmployeeFullName());
        return m;
    }

    private Map<String, Object> mapLoan(LoanRequest r) {
        Map<String, Object> m = new HashMap<>();
        m.put("id",              r.getId());
        m.put("loanType",        r.getLoanType().name());
        m.put("loanTypeLabel",   fmt(r.getLoanType().name()));
        m.put("amount",          r.getAmount());
        m.put("repaymentMonths", r.getRepaymentMonths());
        m.put("reason",          r.getReason());
        m.put("status",          r.getStatus().name());
        m.put("approvedBy",      r.getApprovedBy());
        m.put("rejectedBy",      r.getRejectedBy());
        m.put("hrNote",          r.getHrNote());
        m.put("requestedAt",     r.getRequestedAt());
        m.put("processedAt",     r.getProcessedAt());
        m.put("hasDocument",     r.getVerificationToken() != null);
        m.put("employeeName",    r.getEmployeeFullName());
        // Scoring fields
        m.put("monthlyInstallment",   r.getMonthlyInstallment());
        m.put("riskScore",            r.getRiskScore());
        m.put("systemRecommendation", r.getSystemRecommendation());
        m.put("decisionReason",       r.getDecisionReason());
        m.put("meetingRequired",      r.getMeetingRequired());
        // Include salary for HR risk assessment
        if (r.getUser() != null && r.getUser().getPerson() != null) {
            m.put("employeeSalary",           r.getUser().getPerson().getSalary());
            m.put("employeeHireDate",         r.getUser().getPerson().getHireDate());
            m.put("currentMonthlyDeductions", r.getUser().getPerson().getCurrentMonthlyDeductions());
        }
        return m;
    }

    private Map<String, Object> mapAuth(AuthorizationRequest r) {
        Map<String, Object> m = new HashMap<>();
        m.put("id",                r.getId());
        m.put("authorizationType", r.getAuthorizationType().name());
        m.put("authorizationTypeLabel", fmt(r.getAuthorizationType().name()));
        m.put("startDate",         r.getStartDate());
        m.put("endDate",           r.getEndDate());
        m.put("reason",            r.getReason());
        m.put("status",            r.getStatus().name());
        m.put("approvedBy",        r.getApprovedBy());
        m.put("rejectedBy",        r.getRejectedBy());
        m.put("hrNote",            r.getHrNote());
        m.put("requestedAt",       r.getRequestedAt());
        m.put("processedAt",       r.getProcessedAt());
        m.put("hasDocument",       r.getVerificationToken() != null);
        m.put("employeeName",      r.getEmployeeFullName());
        return m;
    }
}






