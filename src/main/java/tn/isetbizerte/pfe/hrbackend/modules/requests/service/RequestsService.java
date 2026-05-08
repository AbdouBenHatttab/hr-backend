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
import tn.isetbizerte.pfe.hrbackend.modules.user.service.AuthenticatedUserResolver;
import tn.isetbizerte.pfe.hrbackend.infrastructure.storage.DocumentAttachmentStorageService;
import tn.isetbizerte.pfe.hrbackend.infrastructure.storage.StoredAttachment;
import tn.isetbizerte.pfe.hrbackend.infrastructure.storage.UploadFileValidator;
import tn.isetbizerte.pfe.hrbackend.modules.calendar.service.WorkingDayService;
import tn.isetbizerte.pfe.hrbackend.modules.employee.repository.LeaveRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.requests.dto.CreateAuthorizationRequestDto;
import tn.isetbizerte.pfe.hrbackend.modules.requests.dto.CreateDocumentRequestDto;
import tn.isetbizerte.pfe.hrbackend.modules.requests.dto.ValidateAuthorizationDraftRequestDto;
import tn.isetbizerte.pfe.hrbackend.modules.requests.dto.ValidateAuthorizationDraftResponseDto;
import tn.isetbizerte.pfe.hrbackend.modules.requests.dto.ValidateDocumentDraftRequestDto;
import tn.isetbizerte.pfe.hrbackend.modules.requests.dto.ValidateDocumentDraftResponseDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;

@Service
@Slf4j
public class RequestsService {
    // Working-time rule shared between assistant validate-draft and the official
    // createAuthRequest TIME_PERMISSION flow. The day is split into:
    //   Morning   : 08:00 (incl) - 12:00 (excl)
    //   Lunch     : 12:00 - 13:00  BLOCKED
    //   Afternoon : 13:00 (incl) - 17:00 (incl)
    // A range that starts in the morning and ends in the afternoon is rejected
    // because it spans the lunch break.
    private static final LocalTime SHORT_ABSENCE_WORKDAY_START  = LocalTime.of(8,  0);
    private static final LocalTime SHORT_ABSENCE_LUNCH_START    = LocalTime.of(12, 0);
    private static final LocalTime SHORT_ABSENCE_LUNCH_END      = LocalTime.of(13, 0);
    private static final LocalTime SHORT_ABSENCE_WORKDAY_END    = LocalTime.of(17, 0);
    private static final LocalTime LOAN_MEETING_START = LocalTime.of(8, 0);
    private static final LocalTime LOAN_MEETING_LUNCH_START = LocalTime.of(12, 0);
    private static final LocalTime LOAN_MEETING_LATEST_START = LocalTime.of(16, 0);
    private static final LocalTime LOAN_MEETING_WORKDAY_END = LocalTime.of(17, 0);
    private static final Set<LocalTime> LOAN_MEETING_ALLOWED_START_TIMES = Set.of(
            LocalTime.of(8, 0),
            LocalTime.of(9, 0),
            LocalTime.of(10, 0),
            LocalTime.of(11, 0),
            LocalTime.of(13, 0),
            LocalTime.of(14, 0),
            LocalTime.of(15, 0),
            LocalTime.of(16, 0)
    );
    private static final int LOAN_REPAYMENT_MONTHS_COMPATIBILITY_VALUE = 1;
    private static final BigDecimal LOAN_TOTAL_PAYBACK_LOWER_TOLERANCE = new BigDecimal("1.00");
    private static final BigDecimal LOAN_MAX_TOTAL_PAYBACK_MULTIPLIER = new BigDecimal("1.20");

    private final DocumentRequestRepository      documentRepo;
    private final StoredEmployeeDocumentRepository storedDocumentRepo;
    private final LoanRequestRepository          loanRepo;
    private final AuthorizationRequestRepository authRepo;
    private final UserRepository                 userRepository;
    private final PersonRepository               personRepository;
    private final AuthenticatedUserResolver      authenticatedUserResolver;
    private final LoanScoreEngine                loanScoreEngine;
    private final RequestEventProducer           requestEventProducer;
    private final RequestHistoryService          historyService;
    private final DocumentAttachmentStorageService attachmentStorage;
    private final LeaveRequestRepository         leaveRequestRepository;
    private final WorkingDayService              workingDayService;

    public RequestsService(DocumentRequestRepository documentRepo,
                           StoredEmployeeDocumentRepository storedDocumentRepo,
                           LoanRequestRepository loanRepo,
                           AuthorizationRequestRepository authRepo,
                           UserRepository userRepository,
                           PersonRepository personRepository,
                           AuthenticatedUserResolver authenticatedUserResolver,
                           LoanScoreEngine loanScoreEngine,
                           RequestEventProducer requestEventProducer,
                           RequestHistoryService historyService,
                           DocumentAttachmentStorageService attachmentStorage,
                           LeaveRequestRepository leaveRequestRepository,
                           WorkingDayService workingDayService) {
        this.documentRepo     = documentRepo;
        this.storedDocumentRepo = storedDocumentRepo;
        this.loanRepo         = loanRepo;
        this.authRepo         = authRepo;
        this.userRepository   = userRepository;
        this.personRepository = personRepository;
        this.authenticatedUserResolver = authenticatedUserResolver;
        this.loanScoreEngine  = loanScoreEngine;
        this.requestEventProducer  = requestEventProducer;
        this.historyService   = historyService;
        this.attachmentStorage = attachmentStorage;
        this.leaveRequestRepository = leaveRequestRepository;
        this.workingDayService = workingDayService;
    }

    // DOCUMENT REQUESTS

    /**
     * Dry-run document draft validation — no side effects.
     *
     * Applies the same business rules as createDocumentRequest without saving
     * anything, reserving any resource, or publishing any event.
     * Always returns HTTP 200; valid=false signals a rule violation.
     */
    public ValidateDocumentDraftResponseDto validateDocumentDraft(ValidateDocumentDraftRequestDto body) {
        DocumentType type = body.getDocumentType();
        // documentType nullability is already enforced by @NotNull in the DTO;
        // this guard is a safety net in case the method is called directly.
        if (type == null) {
            return ValidateDocumentDraftResponseDto.invalid("documentType is required.");
        }
        if (type == DocumentType.CONTRACT_COPY) {
            return ValidateDocumentDraftResponseDto.invalid(
                    "CONTRACT_COPY is an HR-managed document and cannot be requested by employees.");
        }
        boolean generated = isSystemGeneratedDocument(type);
        String fulfillmentMode = generated ? "GENERATED" : "UPLOADED";
        String message = generated
                ? "This document will be generated automatically by the system once HR approves your request."
                : "HR will prepare and upload this document once your request is approved.";
        return ValidateDocumentDraftResponseDto.valid(type, fulfillmentMode, message);
    }

    /**
     * Dry-run authorization draft validation — no side effects.
     *
     * Applies the same business rules as createAuthRequest but without saving
     * anything, publishing any Kafka/outbox event, or creating history records.
     * Always returns HTTP 200; valid=false signals one or more rule violations.
     *
     * Working-time rule enforced here:
     *   - Morning session: 08:00–12:00
     *   - Lunch break blocked: 12:00–13:00
     *   - Afternoon session: 13:00–17:00
     * This is stricter than the existing createAuthRequest window (08:00–17:00)
     * because the assistant should never propose a time that spans lunch.
     * The real create endpoint remains unchanged.
     *
     * Leave-overlap check requires the caller's identity. Pass jwt=null to skip
     * (e.g. from unit tests that do not need DB interaction).
     */
    public ValidateAuthorizationDraftResponseDto validateAuthorizationDraft(
            ValidateAuthorizationDraftRequestDto body,
            Jwt jwt) {

        AuthorizationType type = body.getAuthorizationType();

        // --- Blocked legacy types ---
        if (type == AuthorizationType.BUSINESS_TRIP || type == AuthorizationType.TRAINING) {
            return ValidateAuthorizationDraftResponseDto.invalid(
                    "Authorization type " + type.name() + " is no longer available for new requests.");
        }

        List<String> errors = new ArrayList<>();

        if (type == AuthorizationType.TIME_PERMISSION) {
            validateTimePermissionDraft(body, jwt, errors);
        } else if (type == AuthorizationType.EQUIPMENT_REQUEST) {
            validateEquipmentRequestDraft(body, errors);
        } else {
            errors.add("Unsupported authorization type: " + type.name());
        }

        if (!errors.isEmpty()) {
            return ValidateAuthorizationDraftResponseDto.invalid(type, errors);
        }

        String message = buildValidMessage(type, body);
        return ValidateAuthorizationDraftResponseDto.valid(type, message);
    }

    // --- TIME_PERMISSION draft validation ---

    private void validateTimePermissionDraft(
            ValidateAuthorizationDraftRequestDto body,
            Jwt jwt,
            List<String> errors) {

        LocalDate date = body.getEffectiveAbsenceDate();
        LocalTime from = body.getFromTime();
        LocalTime to   = body.getToTime();

        if (date == null) {
            errors.add("Short absence requests require a date (absenceDate).");
        }
        if (from == null) {
            errors.add("Short absence requests require fromTime.");
        }
        if (to == null) {
            errors.add("Short absence requests require toTime.");
        }

        // Time-range consistency (only if both provided)
        if (from != null && to != null) {
            if (!to.isAfter(from)) {
                errors.add("toTime must be after fromTime.");
            } else {
                validateTimePermissionWindowDraft(from, to, errors);
            }
        }

        // Date-based rules (only if date provided)
        if (date != null) {
            DayOfWeek dow = date.getDayOfWeek();
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
                errors.add("Short absence date must be a working day. Weekends are not allowed.");
            } else if (workingDayService.isPublicHoliday(date)) {
                errors.add("Short absence date cannot be a public holiday.");
            } else if (jwt != null) {
                // Leave-overlap check — requires an authenticated user
                try {
                    User user = authenticatedUserResolver.require(jwt);
                    var overlaps = leaveRequestRepository.findByUserAndDateRangeAndStatusIn(
                            user, date, date,
                            List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED));
                    if (overlaps.stream().anyMatch(l -> l.getStatus() == LeaveStatus.APPROVED)) {
                        errors.add("Short absence date overlaps an approved leave.");
                    } else if (!overlaps.isEmpty()) {
                        errors.add("Short absence date overlaps a pending leave request.");
                    }
                } catch (Exception e) {
                    log.warn("validateAuthorizationDraft: could not resolve user for leave-overlap check.", e);
                    // Non-fatal: overlap check skipped if user cannot be resolved
                }
            }
        }
    }

    /**
     * Working-window rules for the assistant draft validator.
     *
     * Delegates to the SHARED {@link #validateShortAbsenceWindowRules(LocalTime, LocalTime)}
     * helper so that the dry-run validate-draft endpoint and the real
     * createAuthRequest endpoint apply EXACTLY the same time-window rule.
     *
     * Errors are accumulated into the provided list so that the caller can
     * collect every violation (multiple-error response shape).
     */
    private void validateTimePermissionWindowDraft(
            LocalTime from, LocalTime to, List<String> errors) {
        errors.addAll(validateShortAbsenceWindowRules(from, to));
    }

    /**
     * SHARED working-time rule for TIME_PERMISSION authorization requests.
     *
     * Used by:
     *   - {@link #validateAuthorizationDraft(...)}     (assistant validate-draft)
     *   - {@link #validateShortAbsenceTimeWindow(...)} (official createAuthRequest)
     *
     * Rules:
     *   - Morning   : 08:00 (incl) - 12:00 (excl)
     *   - Lunch     : 12:00 - 13:00  BLOCKED
     *   - Afternoon : 13:00 (incl) - 17:00 (incl)
     *   - Range cannot span the lunch break.
     *
     * Returns an empty list when the times are valid, or a list of error
     * messages otherwise. Caller decides whether to throw or accumulate.
     */
    private static List<String> validateShortAbsenceWindowRules(LocalTime from, LocalTime to) {
        List<String> errors = new ArrayList<>();
        if (from == null || to == null) {
            return errors; // nullability is the caller's responsibility
        }

        boolean fromBeforeLunch = !from.isBefore(SHORT_ABSENCE_WORKDAY_START) && from.isBefore(SHORT_ABSENCE_LUNCH_START);
        boolean fromAfterLunch  = !from.isBefore(SHORT_ABSENCE_LUNCH_END)    && !from.isAfter(SHORT_ABSENCE_WORKDAY_END);
        boolean toBeforeLunch   = !to.isBefore(SHORT_ABSENCE_WORKDAY_START)  && !to.isAfter(SHORT_ABSENCE_LUNCH_START);
        boolean toAfterLunch    = !to.isBefore(SHORT_ABSENCE_LUNCH_END)      && !to.isAfter(SHORT_ABSENCE_WORKDAY_END);

        boolean fromValid = fromBeforeLunch || fromAfterLunch;
        boolean toValid   = toBeforeLunch   || toAfterLunch;

        if (!fromValid) {
            errors.add("fromTime " + from + " is outside working windows (08:00–12:00 or 13:00–17:00).");
            return errors; // no further cross-checks useful
        }
        if (!toValid) {
            errors.add("toTime " + to + " is outside working windows (08:00–12:00 or 13:00–17:00).");
            return errors;
        }

        // Both times in valid zones — reject if they span across lunch
        if (fromBeforeLunch && toAfterLunch) {
            errors.add("The time range " + from + "–" + to
                    + " spans the lunch break (12:00–13:00). "
                    + "Please split into a morning and afternoon request.");
        }
        return errors;
    }

    // --- EQUIPMENT_REQUEST draft validation ---

    private void validateEquipmentRequestDraft(
            ValidateAuthorizationDraftRequestDto body,
            List<String> errors) {

        if (body.getEquipmentType() == null || body.getEquipmentType().trim().isBlank()) {
            errors.add("Equipment type is required.");
        }
        if (body.getReason() == null || body.getReason().trim().isBlank()) {
            errors.add("Reason is required for equipment requests.");
        }
        if (body.getStartDate() == null) {
            errors.add("Needed-from date (startDate) is required.");
        } else if (body.getStartDate().isBefore(LocalDate.now())) {
            errors.add("Needed-from date cannot be in the past.");
        }
        if (body.getStartDate() != null && body.getEndDate() != null
                && body.getEndDate().isBefore(body.getStartDate())) {
            errors.add("Expected return date must be on or after needed-from date.");
        }
    }

    // --- message builder ---

    private String buildValidMessage(AuthorizationType type, ValidateAuthorizationDraftRequestDto body) {
        if (type == AuthorizationType.TIME_PERMISSION) {
            return "Short absence request looks valid. "
                    + "It will be submitted for HR review and you will be notified once a decision is made.";
        }
        if (type == AuthorizationType.EQUIPMENT_REQUEST) {
            return "Equipment borrowing request looks valid. "
                    + "It will be submitted for HR review and you will be notified once a decision is made.";
        }
        return "Authorization request looks valid.";
    }

    @Transactional
    public Map<String, Object> createDocumentRequest(Jwt jwt, CreateDocumentRequestDto body) {
        User user = authenticatedUserResolver.require(jwt);
        DocumentRequest req = new DocumentRequest();
        req.setUser(user);
        DocumentType type = body.getDocumentType();
        if (type == DocumentType.CONTRACT_COPY) {
            throw new BadRequestException("Contract copy is an HR-managed required document and cannot be requested by employees.");
        }
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

    public Page<Map<String, Object>> getMyDocumentRequests(Jwt jwt, Pageable pageable) {
        User user = authenticatedUserResolver.require(jwt);
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

    // DOCUMENT ATTACHMENTS (HR-provided fulfillment)

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

    // HR-MANAGED EMPLOYEE DOCUMENTS

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
        if (isSameKeycloakUser(employee, uploadedByKeycloakId)) {
            throw new BadRequestException("You cannot upload or replace your own HR-managed contract copy.");
        }
        DocumentType type;
        try {
            type = DocumentType.valueOf(documentType);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BadRequestException("Invalid HR-managed document type.");
        }
        if (type != DocumentType.CONTRACT_COPY) {
            throw new BadRequestException("Only contract copies are supported as HR-managed stored documents.");
        }
        if (employee.getRole() != TypeRole.EMPLOYEE && employee.getRole() != TypeRole.TEAM_LEADER) {
            throw new BadRequestException("Contract copies are required only for employees and team leaders.");
        }
        UploadFileValidator.ValidatedFile validatedFile = UploadFileValidator.validate(originalFileName, bytes);

        try {
            List<StoredEmployeeDocument> existingDocuments = storedDocumentRepo
                    .findByEmployeeAndDocumentTypeAndActiveTrueOrderByUploadedAtDesc(employee, type);
            boolean replacingExisting = !existingDocuments.isEmpty();
            for (StoredEmployeeDocument existing : existingDocuments) {
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
            publishRequiredDocumentEvent(employee, doc, uploadedByKeycloakId, replacingExisting);
            return mapStoredDocument(doc);
        } catch (Exception e) {
            log.warn("Stored employee document upload failed. employeeUserId={} actorId={}", employeeUserId, uploadedByKeycloakId, e);
            throw new BadRequestException("Failed to store employee document. Please try again.");
        }
    }

    private void publishRequiredDocumentEvent(User employee, StoredEmployeeDocument doc, String uploadedByKeycloakId, boolean replaced) {
        try {
            requestEventProducer.publish(new RequestEvent(
                    replaced ? "REQUIRED_DOCUMENT_REPLACED" : "REQUIRED_DOCUMENT_UPLOADED",
                    "REQUIRED_DOCUMENT",
                    doc.getId(),
                    employee.getKeycloakId(),
                    uploadedByKeycloakId,
                    doc.getFileName()
            ));
            log.info("Required document event enqueued. eventType={} employeeUserId={} storedDocumentId={}",
                    replaced ? "REQUIRED_DOCUMENT_REPLACED" : "REQUIRED_DOCUMENT_UPLOADED", employee.getId(), doc.getId());
        } catch (Exception e) {
            log.error("Failed to enqueue required document event. Upload remains stored. employeeUserId={} storedDocumentId={} eventType={}",
                    employee.getId(), doc.getId(), replaced ? "REQUIRED_DOCUMENT_REPLACED" : "REQUIRED_DOCUMENT_UPLOADED", e);
        }
    }

    public List<Map<String, Object>> getMyStoredEmployeeDocuments(Jwt jwt) {
        User employee = authenticatedUserResolver.require(jwt);
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

    // LOAN REQUESTS

    @Transactional
    public Map<String, Object> createLoanRequest(Jwt jwt, LoanType loanType,
                                                  BigDecimal amount, Integer repaymentMonths, String reason) {
        User user = authenticatedUserResolver.require(jwt);
        String hardFailReason = getLoanHardFailReason(user, amount);

        LoanRequest req = new LoanRequest();
        req.setUser(user);
        req.setLoanType(loanType);
        req.setAmount(amount);
        req.setRepaymentMonths(repaymentMonths != null ? repaymentMonths : LOAN_REPAYMENT_MONTHS_COMPATIBILITY_VALUE);
        req.setReason(reason);

        if (hardFailReason != null) {
            req.setStatus(RequestStatus.SYSTEM_REJECTED);
            req.setRiskScore(0);
            req.setSystemRecommendation("REJECT");
            req.setDecisionReason(hardFailReason);
            req.setMeetingRequired(false);
            req.setProcessedAt(LocalDateTime.now());
        } else {
            req.setSystemRecommendation("REVIEW");
            req.setDecisionReason("Loan passed hard eligibility checks and is ready for HR review.");
            req.setMeetingRequired(false);
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
     * Loan eligibility rules - objective, cannot be bypassed:
     * 1. Salary must be set by HR before any loan can be requested
     * 2. Max loan = 3 × monthly salary
     * 3. No existing PENDING or active (APPROVED but unfinished) loan
     * 4. Must have been hired at least 6 months ago
     */
    private String getLoanHardFailReason(User user, BigDecimal amount) {
        if (user.getPerson() == null)
            return "SYSTEM-REJECTED: profile is incomplete. Contact HR.";

        // Rule 1 - salary must exist
        BigDecimal salary = user.getPerson().getSalary();
        if (salary == null || salary.compareTo(BigDecimal.ZERO) <= 0)
            return "SYSTEM-REJECTED: salary has not been registered. Contact HR to set up employment details before requesting a loan.";

        // Rule 2 - max 3x monthly salary
        BigDecimal maxAllowed = salary.multiply(new BigDecimal("3"));
        if (amount.compareTo(maxAllowed) > 0)
            return String.format(
                "SYSTEM-REJECTED: requested amount (%.0f TND) exceeds the maximum allowed (3× salary = %.0f TND).",
                amount, maxAllowed);

        // Rule 3 - no existing pending/approved loan
        boolean hasActiveLoan = loanRepo.findByUserOrderByRequestedAtDesc(user).stream()
                .anyMatch(l -> l.getStatus() == RequestStatus.PENDING ||
                               l.getStatus() == RequestStatus.APPROVED);
        if (hasActiveLoan)
            return "SYSTEM-REJECTED: you already have a pending or active loan. You must fully repay your current loan before requesting a new one.";

        // Rule 4 - minimum 6 months employment
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
    public Map<String, Object> getLoanEligibility(Jwt jwt) {
        User user = authenticatedUserResolver.require(jwt);
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

    public Page<Map<String, Object>> getMyLoanRequests(Jwt jwt, Pageable pageable) {
        User user = authenticatedUserResolver.require(jwt);
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
        if (req.getMeetingAt() != null) {
            throw new BadRequestException("Loan cannot be canceled by the employee after a meeting has been scheduled.");
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

    private BigDecimal resolveLoanMaxEligibleAmount(LoanRequest req) {
        if (req.getUser() == null
                || req.getUser().getPerson() == null
                || req.getUser().getPerson().getSalary() == null
                || req.getUser().getPerson().getSalary().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Employee salary is required to validate the maximum eligible loan amount.");
        }
        return req.getUser().getPerson().getSalary().multiply(new BigDecimal("3"));
    }

    @Transactional
    public Map<String, Object> decideLoan(Long id, boolean approve, String hrNote,
                                          Integer repaymentMonths, BigDecimal approvedAmount,
                                          BigDecimal monthlyPayback, String decidedByKeycloakId) {
        return decideLoan(id, approve, hrNote, repaymentMonths, approvedAmount, monthlyPayback, null, decidedByKeycloakId);
    }

    @Transactional
    public Map<String, Object> decideLoan(Long id, boolean approve, String hrNote,
                                          Integer repaymentMonths, BigDecimal approvedAmount,
                                          BigDecimal monthlyPayback, String approvedAmountJustification,
                                          String decidedByKeycloakId) {
        LoanRequest req = loanRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Loan request not found: " + id));
        String note = hrNote != null ? hrNote.trim() : "";
        String amountJustification = approvedAmountJustification != null ? approvedAmountJustification.trim() : "";
        if (req.getUser() != null && decidedByKeycloakId != null
                && decidedByKeycloakId.equals(req.getUser().getKeycloakId())) {
            throw new AccessDeniedException("You cannot approve or reject your own loan request.");
        }
        if (req.getStatus() != RequestStatus.PENDING)
            throw new BadRequestException("Request already " + req.getStatus().name().toLowerCase());
        if (!approve && note.isBlank()) {
            throw new BadRequestException("Rejection reason is required.");
        }
        if (!approve && req.getMeetingAt() != null) {
            throw new BadRequestException("Loan cannot be rejected after a meeting has been scheduled. Cancel after meeting if no agreement is reached.");
        }
        if (approve) {
            if (req.getMeetingAt() == null) {
                throw new BadRequestException("A meeting must be scheduled before approving this loan.");
            }
            if (LocalDateTime.now().isBefore(req.getMeetingAt())) {
                throw new BadRequestException("This loan has a scheduled meeting. Final approval is available after the meeting time.");
            }
            if (repaymentMonths == null) {
                throw new BadRequestException("Repayment duration is required for loan approval.");
            }
            if (repaymentMonths < 1 || repaymentMonths > 60) {
                throw new BadRequestException("Repayment duration must be between 1 and 60 months.");
            }
            if (approvedAmount == null) {
                throw new BadRequestException("Approved amount is required for loan approval.");
            }
            if (approvedAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("Approved amount must be greater than 0.");
            }
            BigDecimal requestedAmount = req.getAmount();
            BigDecimal maxEligibleAmount = resolveLoanMaxEligibleAmount(req);
            if (approvedAmount.compareTo(maxEligibleAmount) > 0) {
                throw new BadRequestException("Approved amount cannot exceed the employee's maximum eligible amount.");
            }
            if (requestedAmount != null && approvedAmount.compareTo(requestedAmount) > 0 && amountJustification.isBlank()) {
                throw new BadRequestException("Justification is required when approving more than the requested amount.");
            }
            if (monthlyPayback == null) {
                throw new BadRequestException("Monthly payback is required for loan approval.");
            }
            if (monthlyPayback.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("Monthly payback must be greater than 0.");
            }
            BigDecimal totalPayback = monthlyPayback.multiply(BigDecimal.valueOf(repaymentMonths));
            if (totalPayback.add(LOAN_TOTAL_PAYBACK_LOWER_TOLERANCE).compareTo(approvedAmount) < 0) {
                throw new BadRequestException("Total payback must cover the approved amount.");
            }
            BigDecimal maxTotalPayback = approvedAmount.multiply(LOAN_MAX_TOTAL_PAYBACK_MULTIPLIER);
            if (totalPayback.compareTo(maxTotalPayback) > 0) {
                throw new BadRequestException("Total payback cannot exceed 20% above the approved amount.");
            }
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
            BigDecimal normalizedApprovedAmount = approvedAmount.setScale(2, RoundingMode.HALF_UP);
            BigDecimal normalizedMonthlyPayback = monthlyPayback.setScale(2, RoundingMode.HALF_UP);
            req.setApprovedAmount(normalizedApprovedAmount);
            req.setApprovedAmountJustification(req.getAmount() != null && approvedAmount.compareTo(req.getAmount()) > 0 ? amountJustification : null);
            req.setRepaymentMonths(repaymentMonths);
            req.setMonthlyInstallment(normalizedMonthlyPayback);
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
        if (req.getMeetingAt() != null) {
            throw new BadRequestException("Loan meeting has already been scheduled and cannot be changed.");
        }
        if (meetingAt == null) {
            throw new BadRequestException("Meeting date and time are required.");
        }
        validateLoanMeetingAvailability(req, meetingAt, decidedByKeycloakId);
        req.setMeetingAt(meetingAt);
        req.setMeetingNote(meetingNote);
        req.setMeetingScheduledBy(decidedByKeycloakId);
        req.setMeetingScheduledAt(LocalDateTime.now());
        loanRepo.save(req);
        historyService.record("LOAN", "HR_SCHEDULED_MEETING", req.getId(), decidedByKeycloakId,
                meetingAt + (meetingNote != null && !meetingNote.isBlank() ? " - " + meetingNote : ""));
        requestEventProducer.publish(
                new RequestEvent(
                        "LOAN_MEETING_SCHEDULED",
                        "LOAN",
                        req.getId(),
                        req.getUser().getKeycloakId(),
                        decidedByKeycloakId,
                        meetingAt.toString()
                )
        );
        return mapLoan(req);
    }

    private void validateLoanMeetingAvailability(LoanRequest req, LocalDateTime meetingAt, String decidedByKeycloakId) {
        if (meetingAt == null) {
            throw new BadRequestException("Meeting date and time are required.");
        }
        LocalDate meetingDate = meetingAt.toLocalDate();
        if (!meetingDate.isAfter(LocalDate.now())) {
            throw new BadRequestException("Loan meetings must be scheduled at least one day in advance.");
        }
        if (!workingDayService.isWorkingDay(meetingDate)) {
            throw new BadRequestException("Loan meetings can only be scheduled on working days.");
        }

        LocalTime startTime = meetingAt.toLocalTime();
        LocalTime endTime = startTime.plusHours(1);
        if (!LOAN_MEETING_ALLOWED_START_TIMES.contains(startTime)
                || startTime.isBefore(LOAN_MEETING_START)
                || startTime.isAfter(LOAN_MEETING_LATEST_START)
                || endTime.isAfter(LOAN_MEETING_WORKDAY_END)
                || startTime.equals(LOAN_MEETING_LUNCH_START)) {
            throw new BadRequestException("Loan meetings must start at an available hourly slot between 08:00 and 16:00, excluding 12:00 lunch break.");
        }

        if (decidedByKeycloakId != null && !decidedByKeycloakId.isBlank()
                && loanRepo.existsScheduledMeetingConflictForHr(req.getId(), decidedByKeycloakId, meetingAt, RequestStatus.PENDING)) {
            throw new BadRequestException("You already have another loan meeting scheduled at this time.");
        }

        if (req.getUser() != null
                && loanRepo.existsScheduledMeetingConflictForEmployee(req.getId(), req.getUser(), meetingAt, RequestStatus.PENDING)) {
            throw new BadRequestException("This employee already has another loan meeting scheduled at this time.");
        }

        if (req.getUser() != null
                && authRepo.existsShortAbsenceOverlap(
                req.getUser(),
                AuthorizationType.TIME_PERMISSION,
                RequestStatus.APPROVED,
                meetingDate,
                startTime,
                endTime
        )) {
            throw new BadRequestException("This employee has an approved short absence during the selected meeting time.");
        }

        var overlaps = leaveRequestRepository.findByUserAndDateRangeAndStatusIn(
                req.getUser(),
                meetingDate,
                meetingDate,
                List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED)
        );
        if (!overlaps.isEmpty()) {
            throw new BadRequestException("This employee is not available on the selected meeting date.");
        }
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
            requestEventProducer.publish(
                    new RequestEvent(
                            "LOAN_FINAL_FILE_READY",
                            "LOAN",
                            req.getId(),
                            req.getUser().getKeycloakId(),
                            decidedByKeycloakId,
                            req.getAttachmentFileName()
                    )
            );
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

    // AUTHORIZATION REQUESTS

    @Transactional
    public Map<String, Object> createAuthRequest(Jwt jwt, CreateAuthorizationRequestDto body) {
        User user = authenticatedUserResolver.require(jwt);
        validateAuthorizationCreation(body);
        AuthorizationRequest req = new AuthorizationRequest();
        req.setUser(user);
        req.setAuthorizationType(body.getAuthorizationType());
        if (body.getAuthorizationType() == AuthorizationType.TIME_PERMISSION) {
            LocalDate absenceDate = body.getEffectiveAbsenceDate();
            validateShortAbsenceAvailability(user, absenceDate);
            validateShortAbsenceTimeWindow(body.getFromTime(), body.getToTime());
            req.setAbsenceDate(absenceDate);
            req.setFromTime(body.getFromTime());
            req.setToTime(body.getToTime());
            req.setStartDate(absenceDate);
            req.setEndDate(absenceDate);
        } else {
            req.setStartDate(body.getStartDate());
            req.setEndDate(body.getEndDate());
            if (body.getAuthorizationType() == AuthorizationType.EQUIPMENT_REQUEST) {
                req.setEquipmentType(body.getEquipmentType().trim());
            }
        }
        req.setReason(body.getReason() != null ? body.getReason().trim() : null);
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

    private void validateAuthorizationCreation(CreateAuthorizationRequestDto body) {
        AuthorizationType type = body.getAuthorizationType();
        if (type == AuthorizationType.BUSINESS_TRIP || type == AuthorizationType.TRAINING) {
            throw new BadRequestException("This authorization type is no longer available for new requests.");
        }
        if (type == AuthorizationType.EQUIPMENT_REQUEST) {
            validateEquipmentRequest(body);
        }
    }

    private void validateEquipmentRequest(CreateAuthorizationRequestDto body) {
        if (body.getEquipmentType() == null || body.getEquipmentType().trim().isBlank()) {
            throw new BadRequestException("Equipment type is required.");
        }
        if (body.getReason() == null || body.getReason().trim().isBlank()) {
            throw new BadRequestException("Reason is required.");
        }
        if (body.getStartDate() == null) {
            throw new BadRequestException("Needed from date is required.");
        }
        if (body.getStartDate().isBefore(LocalDate.now())) {
            throw new BadRequestException("Needed from date cannot be in the past.");
        }
        if (body.getEndDate() != null && body.getEndDate().isBefore(body.getStartDate())) {
            throw new BadRequestException("Expected return date must be on or after needed from date.");
        }
    }

    private void validateShortAbsenceAvailability(User user, LocalDate date) {
        if (date == null) {
            throw new BadRequestException("Short absence requests require a date.");
        }

        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            throw new BadRequestException("Short absence date must be a working day. Weekends are not allowed.");
        }
        if (workingDayService.isPublicHoliday(date)) {
            throw new BadRequestException("Short absence date cannot be a public holiday.");
        }

        var overlaps = leaveRequestRepository.findByUserAndDateRangeAndStatusIn(
                user,
                date,
                date,
                List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED)
        );
        if (overlaps.stream().anyMatch(leave -> leave.getStatus() == LeaveStatus.APPROVED)) {
            throw new BadRequestException("Short absence date overlaps an approved leave.");
        }
        if (!overlaps.isEmpty()) {
            throw new BadRequestException("Short absence date overlaps a pending leave request.");
        }
    }

    /**
     * Time-window validation for the OFFICIAL createAuthRequest TIME_PERMISSION flow.
     *
     * Delegates to the shared {@link #validateShortAbsenceWindowRules(LocalTime, LocalTime)}
     * helper so the official endpoint enforces exactly the same rule as
     * validate-draft (morning 08:00–12:00, lunch blocked 12:00–13:00,
     * afternoon 13:00–17:00, no range spanning lunch).
     *
     * Throws on the first violation to preserve the existing exception-driven
     * contract used by the create endpoint and its tests.
     */
    private void validateShortAbsenceTimeWindow(LocalTime fromTime, LocalTime toTime) {
        if (fromTime == null || toTime == null) {
            throw new BadRequestException("Short absence requests require from time and to time.");
        }
        if (!toTime.isAfter(fromTime)) {
            throw new BadRequestException("To time must be after from time.");
        }
        List<String> errors = validateShortAbsenceWindowRules(fromTime, toTime);
        if (!errors.isEmpty()) {
            throw new BadRequestException(errors.get(0));
        }
    }

    public Page<Map<String, Object>> getMyAuthRequests(Jwt jwt, Pageable pageable) {
        User user = authenticatedUserResolver.require(jwt);
        normalizeLegacyAuthorizationTypes();
        return authRepo.findByUserOrderByRequestedAtDesc(user, pageable)
                .map(this::mapAuth);
    }

    @Transactional
    public Map<String, Object> cancelMyAuthRequest(Long id, String requesterKeycloakId) {
        normalizeLegacyAuthorizationTypes();
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
        normalizeLegacyAuthorizationTypes();
        return authRepo.findAllByOrderByRequestedAtDesc(pageable)
                .map(this::mapAuth);
    }

    @Transactional
    public Map<String, Object> decideAuth(Long id, boolean approve, String hrNote, String decidedByKeycloakId) {
        normalizeLegacyAuthorizationTypes();
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

    private void normalizeLegacyAuthorizationTypes() {
        try {
            int updated = authRepo.normalizeLegacyAuthorizationTypes();
            if (updated > 0) {
                log.warn("Normalized {} legacy authorization request type(s) to TIME_PERMISSION.", updated);
            }
        } catch (Exception e) {
            log.error("Failed to normalize legacy authorization request types before authorization read.", e);
            throw e;
        }
    }

    // PDF GETTERS - used by report controller

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

    // HELPERS

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

    private boolean isSameKeycloakUser(User targetUser, String actorKeycloakId) {
        return targetUser != null
                && actorKeycloakId != null
                && !actorKeycloakId.isBlank()
                && targetUser.getKeycloakId() != null
                && targetUser.getKeycloakId().equals(actorKeycloakId);
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
        m.put("attachmentSizeBytes", r.getAttachmentSizeBytes());
        m.put("employeeName", r.getEmployeeFullName());
        if (r.getUser() != null) {
            m.put("employeeId", r.getUser().getId());
            m.put("employeeUsername", r.getUser().getUsername());
            m.put("employeeRole", r.getUser().getRole() != null ? r.getUser().getRole().name() : null);
            if (r.getUser().getPerson() != null) {
                var person = r.getUser().getPerson();
                m.put("employeeEmail", person.getEmail());
                m.put("employeeDepartment", person.getDepartment());
                m.put("employeeJobTitle", person.getJobTitle());
            }
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
        m.put("approvedAmount",  r.getApprovedAmount());
        m.put("approvedAmountJustification", r.getApprovedAmountJustification());
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
            m.put("employeeId", r.getUser().getId());
            m.put("employeeUsername", r.getUser().getUsername());
            m.put("employeeRole", r.getUser().getRole() != null ? r.getUser().getRole().name() : null);
        }
        // Scoring fields
        m.put("monthlyInstallment",   r.getMonthlyInstallment());
        m.put("monthlyPayback",       r.getMonthlyInstallment());
        if (r.getMonthlyInstallment() != null && r.getRepaymentMonths() != null) {
            BigDecimal totalPayback = r.getMonthlyInstallment().multiply(BigDecimal.valueOf(r.getRepaymentMonths()));
            m.put("totalPayback", totalPayback);
            BigDecimal approvedForPayback = r.getApprovedAmount() != null ? r.getApprovedAmount() : r.getAmount();
            if (approvedForPayback != null) {
                BigDecimal additionalPayback = totalPayback.subtract(approvedForPayback);
                m.put("additionalPayback", additionalPayback.compareTo(BigDecimal.ZERO) > 0 ? additionalPayback : BigDecimal.ZERO);
            }
        }
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
            var person = r.getUser().getPerson();
            m.put("employeeEmail",            person.getEmail());
            m.put("employeeDepartment",       person.getDepartment());
            m.put("employeeJobTitle",         person.getJobTitle());
            m.put("employeeSalary",           person.getSalary());
            m.put("employeeHireDate",         person.getHireDate());
            m.put("currentMonthlyDeductions", person.getCurrentMonthlyDeductions());
            if (person.getHireDate() != null) {
                m.put("employeeSeniorityMonths", ChronoUnit.MONTHS.between(person.getHireDate(), LocalDate.now()));
            }
            if (person.getSalary() != null) {
                BigDecimal maxEligibleAmount = person.getSalary().multiply(new BigDecimal("3"));
                m.put("maxLoan", maxEligibleAmount);
                m.put("maxEligibleAmount", maxEligibleAmount);
            }
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
        m.put("absenceDate",       r.getAbsenceDate());
        m.put("fromTime",          r.getFromTime());
        m.put("toTime",            r.getToTime());
        m.put("equipmentType",     r.getEquipmentType());
        m.put("periodLabel",       authorizationPeriodLabel(r));
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
            m.put("employeeId", r.getUser().getId());
            m.put("employeeUsername", r.getUser().getUsername());
            m.put("employeeRole", r.getUser().getRole() != null ? r.getUser().getRole().name() : null);
            if (r.getUser().getPerson() != null) {
                var person = r.getUser().getPerson();
                m.put("employeeEmail", person.getEmail());
                m.put("employeeDepartment", person.getDepartment());
                m.put("employeeJobTitle", person.getJobTitle());
            }
        }
        return m;
    }

    private String authorizationPeriodLabel(AuthorizationRequest r) {
        if (r.getAuthorizationType() == AuthorizationType.TIME_PERMISSION) {
            LocalDate date = r.getAbsenceDate() != null ? r.getAbsenceDate() : r.getStartDate();
            String dateLabel = date != null ? date.toString() : "Date not specified";
            if (r.getFromTime() != null && r.getToTime() != null) {
                return dateLabel + " · " + formatTime(r.getFromTime()) + "-" + formatTime(r.getToTime());
            }
            return dateLabel;
        }
        if (r.getStartDate() == null && r.getEndDate() == null) return "";
        if (r.getStartDate() != null && r.getEndDate() != null) return r.getStartDate() + " to " + r.getEndDate();
        if (r.getStartDate() != null) return "From " + r.getStartDate();
        return "Until " + r.getEndDate();
    }

    private String formatTime(LocalTime time) {
        return time == null ? "" : time.toString().substring(0, 5);
    }
}






