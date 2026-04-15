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
import tn.isetbizerte.pfe.hrbackend.infrastructure.storage.DocumentAttachmentStorageService;
import tn.isetbizerte.pfe.hrbackend.infrastructure.storage.StoredAttachment;
import tn.isetbizerte.pfe.hrbackend.infrastructure.storage.UploadFileValidator;
import tn.isetbizerte.pfe.hrbackend.modules.requests.dto.CreateAuthorizationRequestDto;
import tn.isetbizerte.pfe.hrbackend.modules.requests.dto.CreateDocumentRequestDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final StoredEmployeeDocumentRepository storedDocumentRepo;
    private final LoanRequestRepository          loanRepo;
    private final AuthorizationRequestRepository authRepo;
    private final UserRepository                 userRepository;
    private final PersonRepository               personRepository;
    private final LoanScoreEngine                loanScoreEngine;
    private final RequestEventProducer           requestEventProducer;
    private final RequestHistoryService          historyService;
    private final DocumentAttachmentStorageService attachmentStorage;

    public RequestsService(DocumentRequestRepository documentRepo,
                           StoredEmployeeDocumentRepository storedDocumentRepo,
                           LoanRequestRepository loanRepo,
                           AuthorizationRequestRepository authRepo,
                           UserRepository userRepository,
                           PersonRepository personRepository,
                           LoanScoreEngine loanScoreEngine,
                           RequestEventProducer requestEventProducer,
                           RequestHistoryService historyService,
                           DocumentAttachmentStorageService attachmentStorage) {
        this.documentRepo     = documentRepo;
        this.storedDocumentRepo = storedDocumentRepo;
        this.loanRepo         = loanRepo;
        this.authRepo         = authRepo;
        this.userRepository   = userRepository;
        this.personRepository = personRepository;
        this.loanScoreEngine  = loanScoreEngine;
        this.requestEventProducer  = requestEventProducer;
        this.historyService   = historyService;
        this.attachmentStorage = attachmentStorage;
    }

    // ─────────────────────────────────────────────────────────────
    // DOCUMENT REQUESTS
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> createDocumentRequest(String username, CreateDocumentRequestDto body) {
        User user = getUser(username);
        DocumentRequest req = new DocumentRequest();
        req.setUser(user);
        DocumentType type = body.getDocumentType();
        req.setDocumentType(type);
        req.setFulfillmentMode(fulfillmentModeFor(type));
        req.setNotes(body.getNotes());
        documentRepo.save(req);
        historyService.record(
                "DOCUMENT",
                "CREATED",
                req.getId(),
                req.getUser().getKeycloakId(),
                req.getNotes()
        );
        try {
            requestEventProducer.publish(
                    new RequestEvent(
                            "DOCUMENT_SUBMITTED",
                            "DOCUMENT",
                            req.getId(),
                            req.getUser().getKeycloakId(),
                            req.getUser().getKeycloakId(),
                            null
                    )
            );
        } catch (Exception e) {
            log.warn("Failed to enqueue DOCUMENT_SUBMITTED request event for requestId={}", req.getId(), e);
        }
        return mapDocument(req);
    }

    public Page<Map<String, Object>> getMyDocumentRequests(String username, Pageable pageable) {
        User user = getUser(username);
        return documentRepo.findByUserOrderByRequestedAtDesc(user, pageable)
                .map(this::mapDocument);
    }

    @Transactional
    public Map<String, Object> cancelMyDocumentRequest(Long id, String requesterKeycloakId) {
        DocumentRequest req = documentRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document request not found: " + id));
        validateOwner(req.getUser().getKeycloakId(), requesterKeycloakId);
        if (req.getStatus() == RequestStatus.CANCELLED_BY_EMPLOYEE) {
            return mapDocument(req);
        }
        if (req.getStatus() != RequestStatus.PENDING) {
            throw new BadRequestException("Only pending document requests can be canceled by the employee.");
        }
        req.setStatus(RequestStatus.CANCELLED_BY_EMPLOYEE);
        req.setCanceledBy(requesterKeycloakId);
        req.setCanceledAt(LocalDateTime.now());
        req.setProcessedAt(req.getCanceledAt());
        documentRepo.save(req);
        historyService.record("DOCUMENT", "EMPLOYEE_CANCELLED", req.getId(), requesterKeycloakId,
                "Employee cancelled the request.", "PENDING_HR", "CANCELLED_BY_EMPLOYEE");
        requestEventProducer.publish(
                new RequestEvent("DOCUMENT_CANCELLED_BY_EMPLOYEE", "DOCUMENT", req.getId(),
                        req.getUser().getKeycloakId(), requesterKeycloakId, null)
        );
        return mapDocument(req);
    }

    public Page<Map<String, Object>> getAllDocumentRequests(Pageable pageable) {
        return documentRepo.findAllByOrderByRequestedAtDesc(pageable)
                .map(this::mapDocument);
    }

    @Transactional
    public Map<String, Object> decideDocument(Long id, boolean approve, String hrNote, String decidedByKeycloakId) {
        DocumentRequest req = documentRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document request not found: " + id));
        String note = hrNote != null ? hrNote.trim() : "";
        if (req.getFulfillmentMode() == null) {
            req.setFulfillmentMode(effectiveMode(req));
        }
        if (req.getUser() != null && decidedByKeycloakId != null
                && decidedByKeycloakId.equals(req.getUser().getKeycloakId())) {
            throw new AccessDeniedException("You cannot approve or reject your own document request.");
        }
        if (req.getStatus() != RequestStatus.PENDING)
            throw new BadRequestException("Request already " + req.getStatus().name().toLowerCase());
        if (!approve && note.isBlank()) {
            throw new BadRequestException("Rejection reason is required.");
        }
        String action = approve ? "HR_APPROVED" : "HR_REJECTED";
        if (historyService.exists("DOCUMENT", action, req.getId(), decidedByKeycloakId)) {
            throw new BadRequestException("This decision was already processed.");
        }
        req.setStatus(approve ? RequestStatus.APPROVED : RequestStatus.REJECTED);
        req.setHrNote(note);
        if (approve) req.setApprovedBy(decidedByKeycloakId);
        else req.setRejectedBy(decidedByKeycloakId);
        req.setProcessedAt(LocalDateTime.now());
        DocumentFulfillmentMode mode = effectiveMode(req);
        if (approve && mode == DocumentFulfillmentMode.GENERATED) {
            req.setVerificationToken(UUID.randomUUID().toString());
        }
        documentRepo.save(req);
        log.info("Document {} by HR. requestId={} actorId={}",
                approve ? "approved" : "rejected", req.getId(), decidedByKeycloakId);
        historyService.record(
                "DOCUMENT",
                action,
                req.getId(),
                decidedByKeycloakId,
                note,
                "PENDING_HR",
                req.getStatus().name()
        );
        String eventType = resolveDocumentDecisionEventType(req, approve);
        requestEventProducer.publish(
                new RequestEvent(
                        eventType,
                        "DOCUMENT",
                        req.getId(),
                        req.getUser().getKeycloakId(),
                        decidedByKeycloakId,
                        note
                )
        );
        return mapDocument(req);
    }

    // ─────────────────────────────────────────────────────────────
    // DOCUMENT ATTACHMENTS (HR-provided fulfillment)
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> uploadDocumentAttachment(Long id, String decidedByKeycloakId, String originalFileName, String contentType, byte[] bytes) {
        DocumentRequest req = documentRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document request not found: " + id));
        if (req.getFulfillmentMode() == null) {
            req.setFulfillmentMode(effectiveMode(req));
        }
        if (req.getStatus() != RequestStatus.APPROVED) {
            throw new BadRequestException("Attachment upload is only allowed for approved requests.");
        }
        if (effectiveMode(req) != DocumentFulfillmentMode.UPLOADED) {
            throw new BadRequestException("This document type is system-generated and does not accept uploaded attachments.");
        }
        if (!acceptsRequestAttachment(req.getDocumentType())) {
            throw new BadRequestException("This document type does not accept uploaded attachments.");
        }
        UploadFileValidator.ValidatedFile validatedFile = UploadFileValidator.validate(originalFileName, bytes);

        try {
            // Replace if exists to keep minimal (one attachment per request).
            attachmentStorage.deleteIfExists(req.getAttachmentStoragePath());
            StoredAttachment stored = attachmentStorage.store(id,
                    originalFileName != null ? originalFileName : "attachment",
                    validatedFile.contentType(),
                    bytes);

            req.setAttachmentStoragePath(stored.getStoragePath());
            req.setAttachmentFileName(stored.getFileName());
            req.setAttachmentContentType(stored.getContentType());
            req.setAttachmentSizeBytes(stored.getSizeBytes());
            req.setAttachmentSha256(stored.getSha256());
            req.setAttachmentUploadedAt(LocalDateTime.now());
            req.setAttachmentUploadedBy(decidedByKeycloakId);

            // Enable public verification only once there's a deliverable file.
            if (req.getVerificationToken() == null || req.getVerificationToken().isBlank()) {
                req.setVerificationToken(UUID.randomUUID().toString());
            }

            documentRepo.save(req);
            historyService.record(
                    "DOCUMENT",
                    "HR_UPLOADED_ATTACHMENT",
                    req.getId(),
                    decidedByKeycloakId,
                    req.getAttachmentFileName(),
                    "APPROVED_WAITING_FOR_HR_FILE",
                    "READY_FOR_DOWNLOAD"
            );
            try {
                requestEventProducer.publish(
                        new RequestEvent(
                                "DOCUMENT_FINAL_FILE_READY",
                                "DOCUMENT",
                                req.getId(),
                                req.getUser().getKeycloakId(),
                                decidedByKeycloakId,
                                req.getAttachmentFileName()
                        )
                );
            } catch (Exception eventError) {
                log.warn("Failed to enqueue DOCUMENT_FINAL_FILE_READY request event for requestId={}", req.getId(), eventError);
            }
            return mapDocument(req);
        } catch (Exception e) {
            log.warn("Attachment store failed. requestId={} actorId={}", id, decidedByKeycloakId, e);
            throw new BadRequestException("Failed to store attachment. Please try again.");
        }
    }

    public DocumentRequest getDocumentRequestForAttachment(Long id, String requesterKeycloakId) {
        DocumentRequest req = documentRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document request not found: " + id));
        validateOwnerOrHR(req.getUser().getKeycloakId(), requesterKeycloakId);
        if (effectiveMode(req) != DocumentFulfillmentMode.UPLOADED) {
            throw new BadRequestException("This document type does not have an uploaded attachment.");
        }
        if (req.getAttachmentStoragePath() == null || req.getAttachmentStoragePath().isBlank()) {
            throw new ResourceNotFoundException("No attachment uploaded yet.");
        }
        return req;
    }

    public byte[] readDocumentAttachment(DocumentRequest req) {
        try {
            return attachmentStorage.read(req.getAttachmentStoragePath());
        } catch (Exception e) {
            throw new ResourceNotFoundException("Attachment not found.");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // HR-MANAGED EMPLOYEE DOCUMENTS
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> uploadStoredEmployeeDocument(Long employeeUserId,
                                                            String documentType,
                                                            String note,
                                                            String uploadedByKeycloakId,
                                                            String originalFileName,
                                                            String contentType,
                                                            byte[] bytes) {
        User employee = userRepository.findById(employeeUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee user not found: " + employeeUserId));
        DocumentType type = DocumentType.valueOf(documentType);
        if (type != DocumentType.CONTRACT_COPY) {
            throw new BadRequestException("Only contract copies are supported as HR-managed stored documents.");
        }
        UploadFileValidator.ValidatedFile validatedFile = UploadFileValidator.validate(originalFileName, bytes);

        try {
            for (StoredEmployeeDocument existing : storedDocumentRepo
                    .findByEmployeeAndDocumentTypeAndActiveTrueOrderByUploadedAtDesc(employee, type)) {
                existing.setActive(false);
                existing.setUpdatedAt(LocalDateTime.now());
            }

            StoredAttachment stored = attachmentStorage.store(employeeUserId,
                    originalFileName != null ? originalFileName : "contract-copy",
                    validatedFile.contentType(),
                    bytes);

            StoredEmployeeDocument doc = new StoredEmployeeDocument();
            doc.setEmployee(employee);
            doc.setDocumentType(type);
            doc.setNote(note);
            doc.setUploadedBy(uploadedByKeycloakId);
            doc.setStoragePath(stored.getStoragePath());
            doc.setFileName(stored.getFileName());
            doc.setContentType(stored.getContentType());
            doc.setSizeBytes(stored.getSizeBytes());
            doc.setSha256(stored.getSha256());
            doc.setUploadedAt(LocalDateTime.now());
            doc.setUpdatedAt(doc.getUploadedAt());
            storedDocumentRepo.save(doc);

            historyService.record("DOCUMENT", "HR_STORED_DOCUMENT", doc.getId(), uploadedByKeycloakId, doc.getFileName());
            return mapStoredDocument(doc);
        } catch (Exception e) {
            log.warn("Stored employee document upload failed. employeeUserId={} actorId={}", employeeUserId, uploadedByKeycloakId, e);
            throw new BadRequestException("Failed to store employee document. Please try again.");
        }
    }

    public List<Map<String, Object>> getMyStoredEmployeeDocuments(String username) {
        User employee = getUser(username);
        return storedDocumentRepo.findByEmployeeAndActiveTrueOrderByUploadedAtDesc(employee).stream()
                .map(this::mapStoredDocument)
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getStoredEmployeeDocumentsForHr(Long employeeUserId) {
        User employee = userRepository.findById(employeeUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee user not found: " + employeeUserId));
        return storedDocumentRepo.findByEmployeeAndActiveTrueOrderByUploadedAtDesc(employee).stream()
                .map(this::mapStoredDocument)
                .collect(Collectors.toList());
    }

    public StoredEmployeeDocument getStoredEmployeeDocumentForDownload(Long id, String requesterKeycloakId) {
        StoredEmployeeDocument doc = storedDocumentRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Stored document not found: " + id));
        if (!doc.isActive()) {
            throw new ResourceNotFoundException("Stored document not found: " + id);
        }
        validateOwnerOrHR(doc.getEmployee().getKeycloakId(), requesterKeycloakId);
        return doc;
    }

    public byte[] readStoredEmployeeDocument(StoredEmployeeDocument doc) {
        try {
            return attachmentStorage.read(doc.getStoragePath());
        } catch (Exception e) {
            throw new ResourceNotFoundException("Stored document file not found.");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // LOAN REQUESTS
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> createLoanRequest(String username, LoanType loanType,
                                                  BigDecimal amount, int repaymentMonths, String reason) {
        User user = getUser(username);
        String hardFailReason = getLoanHardFailReason(user, amount);

        LoanRequest req = new LoanRequest();
        req.setUser(user);
        req.setLoanType(loanType);
        req.setAmount(amount);
        req.setRepaymentMonths(repaymentMonths);
        req.setReason(reason);

        if (hardFailReason != null) {
            req.setStatus(RequestStatus.SYSTEM_REJECTED);
            req.setRiskScore(0);
            req.setSystemRecommendation("REJECT");
            req.setDecisionReason(hardFailReason);
            req.setMeetingRequired(false);
            req.setMonthlyInstallment(amount.divide(BigDecimal.valueOf(repaymentMonths), 2, RoundingMode.HALF_UP));
            req.setProcessedAt(LocalDateTime.now());
        } else {
            // Run scoring engine — sets monthlyInstallment, riskScore, recommendation, decisionReason
            loanScoreEngine.evaluate(req);

            if (isSystemHardReject(req)) {
                req.setStatus(RequestStatus.SYSTEM_REJECTED);
                req.setProcessedAt(LocalDateTime.now());
            }
        }

        loanRepo.save(req);
        historyService.record(
                "LOAN",
                req.getStatus() == RequestStatus.SYSTEM_REJECTED ? "SYSTEM_REJECTED" : "CREATED",
                req.getId(),
                req.getUser().getKeycloakId(),
                req.getStatus() == RequestStatus.SYSTEM_REJECTED ? req.getDecisionReason() : req.getReason()
        );
        try {
            requestEventProducer.publish(
                    new RequestEvent(
                            req.getStatus() == RequestStatus.SYSTEM_REJECTED ? "LOAN_SYSTEM_REJECTED" : "LOAN_SUBMITTED",
                            "LOAN",
                            req.getId(),
                            req.getUser().getKeycloakId(),
                            req.getUser().getKeycloakId(),
                            null
                    )
            );
        } catch (Exception e) {
            log.warn("Failed to enqueue LOAN_SUBMITTED request event for requestId={}", req.getId(), e);
        }
        return mapLoan(req);
    }

    /**
     * Loan eligibility rules — objective, cannot be bypassed:
     * 1. Salary must be set by HR before any loan can be requested
     * 2. Max loan = 3 × monthly salary
     * 3. No existing PENDING or active (APPROVED but unfinished) loan
     * 4. Must have been hired at least 6 months ago
     */
    private String getLoanHardFailReason(User user, BigDecimal amount) {
        if (user.getPerson() == null)
            return "SYSTEM-REJECTED: profile is incomplete. Contact HR.";

        // Rule 1 — salary must exist
        BigDecimal salary = user.getPerson().getSalary();
        if (salary == null || salary.compareTo(BigDecimal.ZERO) <= 0)
            return "SYSTEM-REJECTED: salary has not been registered. Contact HR to set up employment details before requesting a loan.";

        // Rule 2 — max 3x monthly salary
        BigDecimal maxAllowed = salary.multiply(new BigDecimal("3"));
        if (amount.compareTo(maxAllowed) > 0)
            return String.format(
                "SYSTEM-REJECTED: requested amount (%.0f TND) exceeds the maximum allowed (3× salary = %.0f TND).",
                amount, maxAllowed);

        // Rule 3 — no existing pending/approved loan
        boolean hasActiveLoan = loanRepo.findByUserOrderByRequestedAtDesc(user).stream()
                .anyMatch(l -> l.getStatus() == RequestStatus.PENDING ||
                               l.getStatus() == RequestStatus.APPROVED);
        if (hasActiveLoan)
            return "SYSTEM-REJECTED: you already have a pending or active loan. You must fully repay your current loan before requesting a new one.";

        // Rule 4 — minimum 6 months employment
        if (user.getPerson().getHireDate() != null) {
            long monthsEmployed = java.time.temporal.ChronoUnit.MONTHS.between(
                    user.getPerson().getHireDate(), java.time.LocalDate.now());
            if (monthsEmployed < 6)
                return String.format(
                    "SYSTEM-REJECTED: you must be employed for at least 6 months before requesting a loan. You have been employed for %d month(s).",
                    monthsEmployed);
        }
        return null;
    }

    private boolean isSystemHardReject(LoanRequest req) {
        return "REJECT".equals(req.getSystemRecommendation())
                && req.getDecisionReason() != null
                && req.getDecisionReason().contains("AUTO-REJECTED");
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

    @Transactional
    public Map<String, Object> cancelMyLoanRequest(Long id, String requesterKeycloakId) {
        LoanRequest req = loanRepo.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Loan request not found: " + id));
        validateOwner(req.getUser().getKeycloakId(), requesterKeycloakId);
        if (req.getStatus() == RequestStatus.CANCELLED_BY_EMPLOYEE) {
            return mapLoan(req);
        }
        if (req.getStatus() != RequestStatus.PENDING) {
            throw new BadRequestException("Only pending loan requests can be canceled by the employee.");
        }
        LocalDateTime canceledAt = LocalDateTime.now();
        req.setStatus(RequestStatus.CANCELLED_BY_EMPLOYEE);
        req.setCanceledBy(requesterKeycloakId);
        req.setCanceledAt(canceledAt);
        req.setProcessedAt(canceledAt);
        loanRepo.save(req);
        historyService.record("LOAN", "EMPLOYEE_CANCELLED", req.getId(), requesterKeycloakId,
                "Employee cancelled the request.", "PENDING_HR", "CANCELLED_BY_EMPLOYEE");
        requestEventProducer.publish(
                new RequestEvent("LOAN_CANCELLED_BY_EMPLOYEE", "LOAN", req.getId(),
                        req.getUser().getKeycloakId(), requesterKeycloakId, null)
        );
        return mapLoan(req);
    }

    public Page<Map<String, Object>> getAllLoanRequests(Pageable pageable) {
        return loanRepo.findAllByOrderByRequestedAtDesc(pageable)
                .map(this::mapLoan);
    }

    @Transactional
    public Map<String, Object> decideLoan(Long id, boolean approve, String hrNote, String decidedByKeycloakId) {
        LoanRequest req = loanRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Loan request not found: " + id));
        String note = hrNote != null ? hrNote.trim() : "";
        if (req.getUser() != null && decidedByKeycloakId != null
                && decidedByKeycloakId.equals(req.getUser().getKeycloakId())) {
            throw new AccessDeniedException("You cannot approve or reject your own loan request.");
        }
        if (req.getStatus() != RequestStatus.PENDING)
            throw new BadRequestException("Request already " + req.getStatus().name().toLowerCase());
        if (!approve && note.isBlank()) {
            throw new BadRequestException("Rejection reason is required.");
        }
        String action = approve ? "HR_APPROVED" : "HR_REJECTED";
        if (historyService.exists("LOAN", action, req.getId(), decidedByKeycloakId)) {
            throw new BadRequestException("This decision was already processed.");
        }
        LocalDateTime decidedAt = LocalDateTime.now();
        req.setStatus(approve ? RequestStatus.APPROVED : RequestStatus.REJECTED);
        req.setHrNote(note);
        req.setProcessedAt(decidedAt);
        if (approve) {
            req.setApprovedBy(decidedByKeycloakId);
            req.setApprovedAt(decidedAt);
            req.setVerificationToken(UUID.randomUUID().toString());
            // Update monthly deductions
            if (req.getMonthlyInstallment() != null
                    && req.getUser() != null
                    && req.getUser().getPerson() != null) {
                var person = req.getUser().getPerson();
                BigDecimal current = person.getCurrentMonthlyDeductions() != null
                        ? person.getCurrentMonthlyDeductions()
                        : BigDecimal.ZERO;
                person.setCurrentMonthlyDeductions(current.add(req.getMonthlyInstallment()));
                personRepository.save(person);
            }
        } else {
            req.setRejectedBy(decidedByKeycloakId);
            req.setRejectedAt(decidedAt);
            req.setHrDecisionReason(note);
            req.setHrDecisionStage(resolveLoanDecisionStage(req, decidedAt));
        }
        loanRepo.save(req);
        log.info("Loan {} by HR. requestId={} actorId={}",
                approve ? "approved" : "rejected", req.getId(), decidedByKeycloakId);
        historyService.record(
                "LOAN",
                action,
                req.getId(),
                decidedByKeycloakId,
                note,
                "PENDING_HR",
                req.getStatus().name()
        );
        requestEventProducer.publish(
                new RequestEvent(
                        approve ? "LOAN_APPROVED" : "LOAN_REJECTED",
                        "LOAN",
                        req.getId(),
                        req.getUser().getKeycloakId(),
                        decidedByKeycloakId,
                        note
                )
        );
        return mapLoan(req);
    }

    @Transactional
    public Map<String, Object> scheduleLoanMeeting(Long id, LocalDateTime meetingAt, String meetingNote, String decidedByKeycloakId) {
        LoanRequest req = loanRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Loan request not found: " + id));
        if (req.getUser() != null && decidedByKeycloakId != null
                && decidedByKeycloakId.equals(req.getUser().getKeycloakId())) {
            throw new AccessDeniedException("You cannot schedule a meeting for your own loan request.");
        }
        if (req.getStatus() != RequestStatus.PENDING) {
            throw new BadRequestException("Meeting can only be scheduled for loans pending HR review.");
        }
        if (meetingAt == null) {
            throw new BadRequestException("Meeting date and time are required.");
        }
        req.setMeetingAt(meetingAt);
        req.setMeetingNote(meetingNote);
        req.setMeetingScheduledBy(decidedByKeycloakId);
        req.setMeetingScheduledAt(LocalDateTime.now());
        loanRepo.save(req);
        historyService.record("LOAN", "HR_SCHEDULED_MEETING", req.getId(), decidedByKeycloakId,
                meetingAt + (meetingNote != null && !meetingNote.isBlank() ? " - " + meetingNote : ""));
        return mapLoan(req);
    }

    @Transactional
    public Map<String, Object> cancelLoanAfterMeeting(Long id, String reason, String canceledByKeycloakId) {
        LoanRequest req = loanRepo.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Loan request not found: " + id));
        String cancellationReason = reason != null ? reason.trim() : "";
        if (cancellationReason.isBlank()) {
            throw new BadRequestException("Cancellation reason is required.");
        }
        if (req.getStatus() == RequestStatus.CANCELLED_AFTER_MEETING) {
            return mapLoan(req);
        }
        if (req.getUser() != null && canceledByKeycloakId != null
                && canceledByKeycloakId.equals(req.getUser().getKeycloakId())) {
            throw new AccessDeniedException("You cannot cancel your own loan request.");
        }
        if (req.getStatus() != RequestStatus.PENDING) {
            throw new BadRequestException("Only loans pending HR review can be canceled after a meeting.");
        }
        if (req.getMeetingAt() == null) {
            throw new BadRequestException("A meeting must be scheduled before this loan can be canceled after meeting.");
        }
        LocalDateTime canceledAt = LocalDateTime.now();
        if (canceledAt.isBefore(req.getMeetingAt())) {
            throw new BadRequestException("Loan can only be canceled after the scheduled meeting date and time has passed.");
        }
        if (req.getAttachmentStoragePath() != null && !req.getAttachmentStoragePath().isBlank()) {
            throw new BadRequestException("Loan already has a final file and cannot be canceled after meeting.");
        }
        if (historyService.exists("LOAN", "HR_CANCELLED_AFTER_MEETING", req.getId(), canceledByKeycloakId)) {
            return mapLoan(req);
        }

        req.setStatus(RequestStatus.CANCELLED_AFTER_MEETING);
        req.setCancellationReason(cancellationReason);
        req.setCanceledBy(canceledByKeycloakId);
        req.setCanceledAt(canceledAt);
        req.setProcessedAt(canceledAt);
        req.setHrDecisionReason(cancellationReason);
        req.setHrDecisionStage("AFTER_MEETING");
        loanRepo.save(req);
        log.info("Loan canceled after meeting by HR. requestId={} actorId={}", req.getId(), canceledByKeycloakId);
        historyService.record(
                "LOAN",
                "HR_CANCELLED_AFTER_MEETING",
                req.getId(),
                canceledByKeycloakId,
                cancellationReason
        );
        requestEventProducer.publish(
                new RequestEvent(
                        "LOAN_CANCELLED_AFTER_MEETING",
                        "LOAN",
                        req.getId(),
                        req.getUser().getKeycloakId(),
                        canceledByKeycloakId,
                        cancellationReason
                )
        );
        return mapLoan(req);
    }

    @Transactional
    public Map<String, Object> uploadLoanAttachment(Long id, String decidedByKeycloakId, String originalFileName, String contentType, byte[] bytes) {
        LoanRequest req = loanRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Loan request not found: " + id));
        if (req.getUser() != null && decidedByKeycloakId != null
                && decidedByKeycloakId.equals(req.getUser().getKeycloakId())) {
            throw new AccessDeniedException("You cannot upload the final file for your own loan request.");
        }
        if (req.getStatus() != RequestStatus.APPROVED) {
            throw new BadRequestException("Final loan file upload is only allowed after HR approval.");
        }
        UploadFileValidator.ValidatedFile validatedFile = UploadFileValidator.validate(originalFileName, bytes);

        try {
            attachmentStorage.deleteIfExists(req.getAttachmentStoragePath());
            StoredAttachment stored = attachmentStorage.store(id,
                    originalFileName != null ? originalFileName : "loan-final-file",
                    validatedFile.contentType(),
                    bytes);

            req.setAttachmentStoragePath(stored.getStoragePath());
            req.setAttachmentFileName(stored.getFileName());
            req.setAttachmentContentType(stored.getContentType());
            req.setAttachmentSizeBytes(stored.getSizeBytes());
            req.setAttachmentSha256(stored.getSha256());
            req.setAttachmentUploadedAt(LocalDateTime.now());
            req.setAttachmentUploadedBy(decidedByKeycloakId);
            loanRepo.save(req);

            historyService.record("LOAN", "HR_UPLOADED_FINAL_FILE", req.getId(), decidedByKeycloakId, req.getAttachmentFileName());
            return mapLoan(req);
        } catch (Exception e) {
            log.warn("Loan final file store failed. requestId={} actorId={}", id, decidedByKeycloakId, e);
            throw new BadRequestException("Failed to store final loan file. Please try again.");
        }
    }

    public LoanRequest getLoanRequestForAttachment(Long id, String requesterKeycloakId) {
        LoanRequest req = loanRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Loan request not found: " + id));
        validateOwnerOrHR(req.getUser().getKeycloakId(), requesterKeycloakId);
        if (req.getStatus() != RequestStatus.APPROVED) {
            throw new BadRequestException("Final loan file is only available for approved loans.");
        }
        if (req.getAttachmentStoragePath() == null || req.getAttachmentStoragePath().isBlank()) {
            throw new ResourceNotFoundException("No final loan file uploaded yet.");
        }
        return req;
    }

    public byte[] readLoanAttachment(LoanRequest req) {
        try {
            return attachmentStorage.read(req.getAttachmentStoragePath());
        } catch (Exception e) {
            throw new ResourceNotFoundException("Final loan file not found.");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // AUTHORIZATION REQUESTS
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> createAuthRequest(String username, CreateAuthorizationRequestDto body) {
        User user = getUser(username);
        AuthorizationRequest req = new AuthorizationRequest();
        req.setUser(user);
        req.setAuthorizationType(body.getAuthorizationType());
        req.setStartDate(body.getStartDate());
        req.setEndDate(body.getEndDate());
        req.setReason(body.getReason());
        authRepo.save(req);
        historyService.record(
                "AUTH",
                "CREATED",
                req.getId(),
                req.getUser().getKeycloakId(),
                req.getReason()
        );
        try {
            requestEventProducer.publish(
                    new RequestEvent(
                            "AUTH_SUBMITTED",
                            "AUTH",
                            req.getId(),
                            req.getUser().getKeycloakId(),
                            req.getUser().getKeycloakId(),
                            null
                    )
            );
        } catch (Exception e) {
            log.warn("Failed to enqueue AUTH_SUBMITTED request event for requestId={}", req.getId(), e);
        }
        return mapAuth(req);
    }

    public Page<Map<String, Object>> getMyAuthRequests(String username, Pageable pageable) {
        User user = getUser(username);
        return authRepo.findByUserOrderByRequestedAtDesc(user, pageable)
                .map(this::mapAuth);
    }

    @Transactional
    public Map<String, Object> cancelMyAuthRequest(Long id, String requesterKeycloakId) {
        AuthorizationRequest req = authRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Authorization request not found: " + id));
        validateOwner(req.getUser().getKeycloakId(), requesterKeycloakId);
        if (req.getStatus() == RequestStatus.CANCELLED_BY_EMPLOYEE) {
            return mapAuth(req);
        }
        if (req.getStatus() != RequestStatus.PENDING) {
            throw new BadRequestException("Only pending authorization requests can be canceled by the employee.");
        }
        req.setStatus(RequestStatus.CANCELLED_BY_EMPLOYEE);
        req.setCanceledBy(requesterKeycloakId);
        req.setCanceledAt(LocalDateTime.now());
        req.setProcessedAt(req.getCanceledAt());
        authRepo.save(req);
        historyService.record("AUTH", "EMPLOYEE_CANCELLED", req.getId(), requesterKeycloakId,
                "Employee cancelled the request.", "PENDING_HR", "CANCELLED_BY_EMPLOYEE");
        requestEventProducer.publish(
                new RequestEvent("AUTH_CANCELLED_BY_EMPLOYEE", "AUTH", req.getId(),
                        req.getUser().getKeycloakId(), requesterKeycloakId, null)
        );
        return mapAuth(req);
    }

    public Page<Map<String, Object>> getAllAuthRequests(Pageable pageable) {
        return authRepo.findAllByOrderByRequestedAtDesc(pageable)
                .map(this::mapAuth);
    }

    @Transactional
    public Map<String, Object> decideAuth(Long id, boolean approve, String hrNote, String decidedByKeycloakId) {
        AuthorizationRequest req = authRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Authorization request not found: " + id));
        String note = hrNote != null ? hrNote.trim() : "";
        if (req.getUser() != null && decidedByKeycloakId != null
                && decidedByKeycloakId.equals(req.getUser().getKeycloakId())) {
            throw new AccessDeniedException("You cannot approve or reject your own authorization request.");
        }
        if (req.getStatus() != RequestStatus.PENDING)
            throw new BadRequestException("Request already " + req.getStatus().name().toLowerCase());
        if (!approve && note.isBlank()) {
            throw new BadRequestException("Rejection reason is required.");
        }
        String action = approve ? "HR_APPROVED" : "HR_REJECTED";
        if (historyService.exists("AUTH", action, req.getId(), decidedByKeycloakId)) {
            throw new BadRequestException("This decision was already processed.");
        }
        req.setStatus(approve ? RequestStatus.APPROVED : RequestStatus.REJECTED);
        req.setHrNote(note);
        if (approve) req.setApprovedBy(decidedByKeycloakId);
        else req.setRejectedBy(decidedByKeycloakId);
        req.setProcessedAt(LocalDateTime.now());
        authRepo.save(req);
        log.info("Authorization {} by HR. requestId={} actorId={}",
                approve ? "approved" : "rejected", req.getId(), decidedByKeycloakId);
        historyService.record(
                "AUTH",
                action,
                req.getId(),
                decidedByKeycloakId,
                note,
                "PENDING_HR",
                req.getStatus().name()
        );
        requestEventProducer.publish(
                new RequestEvent(
                        approve ? "AUTH_APPROVED" : "AUTH_REJECTED",
                        "AUTH",
                        req.getId(),
                        req.getUser().getKeycloakId(),
                        decidedByKeycloakId,
                        note
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
        if (effectiveMode(req) != DocumentFulfillmentMode.GENERATED) {
            throw new BadRequestException("This document is HR-prepared and must be downloaded from the uploaded final file.");
        }
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

    private void validateOwner(String ownerKeycloakId, String requesterKeycloakId) {
        if (ownerKeycloakId == null || !ownerKeycloakId.equals(requesterKeycloakId)) {
            throw new AccessDeniedException("Only the owner can cancel this request.");
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
        DocumentFulfillmentMode mode = effectiveMode(r);
        boolean hasAttachment = r.getAttachmentStoragePath() != null && !r.getAttachmentStoragePath().isBlank();
        boolean generatedReady = mode == DocumentFulfillmentMode.GENERATED && r.getStatus() == RequestStatus.APPROVED
                && r.getVerificationToken() != null && !r.getVerificationToken().isBlank();
        boolean finalReady = mode == DocumentFulfillmentMode.UPLOADED ? hasAttachment : generatedReady;
        m.put("id",           r.getId());
        m.put("documentType", r.getDocumentType().name());
        m.put("documentTypeLabel", fmt(r.getDocumentType().name()));
        m.put("fulfillmentMode", mode.name());
        m.put("fulfillmentStatus", fulfillmentStatus(r));
        m.put("finalDocumentReady", finalReady);
        m.put("requiresHrUpload", mode == DocumentFulfillmentMode.UPLOADED);
        m.put("notes",        r.getNotes());
        m.put("status",       r.getStatus().name());
        m.put("approvedBy",   r.getApprovedBy());
        m.put("rejectedBy",   r.getRejectedBy());
        m.put("canceledBy",   r.getCanceledBy());
        m.put("canceledAt",   r.getCanceledAt());
        m.put("hrNote",       r.getHrNote());
        m.put("requestedAt",  r.getRequestedAt());
        m.put("processedAt",  r.getProcessedAt());
        m.put("hasDocument",  finalReady);
        m.put("hasAttachment", hasAttachment);
        m.put("attachmentFileName", r.getAttachmentFileName());
        m.put("attachmentUploadedAt", r.getAttachmentUploadedAt());
        m.put("employeeName", r.getEmployeeFullName());
        if (r.getUser() != null) {
            m.put("employeeUsername", r.getUser().getUsername());
        }
        return m;
    }

    private boolean isHrProvided(DocumentType type) {
        return !isSystemGeneratedDocument(type);
    }

    private boolean acceptsRequestAttachment(DocumentType type) {
        return isHrProvided(type);
    }

    private DocumentFulfillmentMode effectiveMode(DocumentRequest req) {
        if (req.getFulfillmentMode() != null) return req.getFulfillmentMode();
        return fulfillmentModeFor(req.getDocumentType());
    }

    private DocumentFulfillmentMode fulfillmentModeFor(DocumentType type) {
        return isSystemGeneratedDocument(type)
                ? DocumentFulfillmentMode.GENERATED
                : DocumentFulfillmentMode.UPLOADED;
    }

    private boolean isSystemGeneratedDocument(DocumentType type) {
        return type == DocumentType.LEAVE_BALANCE_STATEMENT;
    }

    private String fulfillmentStatus(DocumentRequest req) {
        if (req.getStatus() == RequestStatus.PENDING) return "PENDING";
        if (req.getStatus() == RequestStatus.REJECTED) return "REJECTED";
        if (req.getStatus() == RequestStatus.CANCELLED_BY_EMPLOYEE) return "CANCELLED_BY_EMPLOYEE";
        if (req.getStatus() != RequestStatus.APPROVED) return req.getStatus().name();
        DocumentFulfillmentMode mode = effectiveMode(req);
        if (mode == DocumentFulfillmentMode.GENERATED) return "READY_FOR_DOWNLOAD";
        boolean hasAttachment = req.getAttachmentStoragePath() != null && !req.getAttachmentStoragePath().isBlank();
        return hasAttachment ? "READY_FOR_DOWNLOAD" : "APPROVED_WAITING_FOR_HR_FILE";
    }

    private String resolveDocumentDecisionEventType(DocumentRequest req, boolean approved) {
        if (!approved) return "DOCUMENT_REJECTED";
        if (effectiveMode(req) == DocumentFulfillmentMode.GENERATED
                && req.getVerificationToken() != null
                && !req.getVerificationToken().isBlank()) {
            return "DOCUMENT_READY";
        }
        return "DOCUMENT_APPROVED";
    }

    private Map<String, Object> mapStoredDocument(StoredEmployeeDocument d) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", d.getId());
        m.put("documentType", d.getDocumentType().name());
        m.put("documentTypeLabel", fmt(d.getDocumentType().name()));
        m.put("fileName", d.getFileName());
        m.put("contentType", d.getContentType());
        m.put("sizeBytes", d.getSizeBytes());
        m.put("note", d.getNote());
        m.put("uploadedAt", d.getUploadedAt());
        m.put("uploadedBy", d.getUploadedBy());
        m.put("active", d.isActive());
        if (d.getEmployee() != null) {
            m.put("employeeId", d.getEmployee().getId());
            m.put("employeeUsername", d.getEmployee().getUsername());
            m.put("employeeName", employeeName(d.getEmployee()));
        }
        return m;
    }

    private String employeeName(User user) {
        if (user != null && user.getPerson() != null) {
            return user.getPerson().getFirstName() + " " + user.getPerson().getLastName();
        }
        return user != null ? user.getUsername() : "Unknown";
    }

    private String resolveLoanDecisionStage(LoanRequest req, LocalDateTime decidedAt) {
        return req.getMeetingAt() != null && !decidedAt.isBefore(req.getMeetingAt())
                ? "AFTER_MEETING"
                : "BEFORE_MEETING";
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
        m.put("approvedAt",      r.getApprovedAt());
        m.put("rejectedBy",      r.getRejectedBy());
        m.put("rejectedAt",      r.getRejectedAt());
        m.put("canceledBy",       r.getCanceledBy());
        m.put("canceledAt",       r.getCanceledAt());
        m.put("hrDecisionReason", r.getHrDecisionReason());
        m.put("hrDecisionStage", r.getHrDecisionStage());
        m.put("cancellationReason", r.getCancellationReason());
        m.put("canceledBy",       r.getCanceledBy());
        m.put("canceledAt",       r.getCanceledAt());
        m.put("hrNote",          r.getHrNote());
        m.put("requestedAt",     r.getRequestedAt());
        m.put("processedAt",     r.getProcessedAt());
        m.put("hasDocument",     r.getVerificationToken() != null);
        m.put("hasAttachment",   r.getAttachmentStoragePath() != null && !r.getAttachmentStoragePath().isBlank());
        m.put("attachmentFileName", r.getAttachmentFileName());
        m.put("attachmentUploadedAt", r.getAttachmentUploadedAt());
        m.put("attachmentSizeBytes", r.getAttachmentSizeBytes());
        m.put("employeeName",    r.getEmployeeFullName());
        if (r.getUser() != null) {
            m.put("employeeUsername", r.getUser().getUsername());
        }
        // Scoring fields
        m.put("monthlyInstallment",   r.getMonthlyInstallment());
        m.put("riskScore",            r.getRiskScore());
        m.put("systemRecommendation", r.getSystemRecommendation());
        m.put("decisionReason",       r.getDecisionReason());
        m.put("meetingRequired",      r.getMeetingRequired());
        m.put("meetingAt",            r.getMeetingAt());
        m.put("meetingNote",          r.getMeetingNote());
        m.put("meetingScheduledBy",   r.getMeetingScheduledBy());
        m.put("meetingScheduledAt",   r.getMeetingScheduledAt());
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
        m.put("canceledBy",        r.getCanceledBy());
        m.put("canceledAt",        r.getCanceledAt());
        m.put("hrNote",            r.getHrNote());
        m.put("requestedAt",       r.getRequestedAt());
        m.put("processedAt",       r.getProcessedAt());
        m.put("hasDocument",       false);
        m.put("confirmationChannel", "IN_APP_NOTIFICATION_AND_EMAIL");
        m.put("employeeName",      r.getEmployeeFullName());
        if (r.getUser() != null) {
            m.put("employeeUsername", r.getUser().getUsername());
        }
        return m;
    }
}






