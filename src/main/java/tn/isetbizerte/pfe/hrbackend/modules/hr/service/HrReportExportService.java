package tn.isetbizerte.pfe.hrbackend.modules.hr.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.isetbizerte.pfe.hrbackend.common.enums.ApprovalDecision;
import tn.isetbizerte.pfe.hrbackend.common.enums.LeaveStatus;
import tn.isetbizerte.pfe.hrbackend.common.enums.RequestStatus;
import tn.isetbizerte.pfe.hrbackend.common.enums.TypeRole;
import tn.isetbizerte.pfe.hrbackend.common.exception.UnauthorizedException;
import tn.isetbizerte.pfe.hrbackend.modules.employee.entity.LeaveRequest;
import tn.isetbizerte.pfe.hrbackend.modules.employee.repository.LeaveRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.hr.dto.HrReportExportRequest;
import tn.isetbizerte.pfe.hrbackend.modules.hr.dto.HrReportRowDto;
import tn.isetbizerte.pfe.hrbackend.modules.requests.entity.AuthorizationRequest;
import tn.isetbizerte.pfe.hrbackend.modules.requests.entity.DocumentRequest;
import tn.isetbizerte.pfe.hrbackend.modules.requests.entity.LoanRequest;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.AuthorizationRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.DocumentRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.LoanRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.Person;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.UserRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.service.AuthenticatedUserResolver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class HrReportExportService {

    private static final String DEFAULT_TITLE = "ArabSoft HR Reports";

    private final LeaveRequestRepository leaveRequestRepository;
    private final DocumentRequestRepository documentRequestRepository;
    private final LoanRequestRepository loanRequestRepository;
    private final AuthorizationRequestRepository authorizationRequestRepository;
    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final UserRepository userRepository;
    private final HRService hrService;
    private final Clock clock;

    @Autowired
    public HrReportExportService(LeaveRequestRepository leaveRequestRepository,
                                 DocumentRequestRepository documentRequestRepository,
                                 LoanRequestRepository loanRequestRepository,
                                 AuthorizationRequestRepository authorizationRequestRepository,
                                 AuthenticatedUserResolver authenticatedUserResolver,
                                 UserRepository userRepository,
                                 HRService hrService) {
        this(leaveRequestRepository, documentRequestRepository, loanRequestRepository,
                authorizationRequestRepository, authenticatedUserResolver, userRepository, hrService, Clock.systemDefaultZone());
    }

    HrReportExportService(LeaveRequestRepository leaveRequestRepository,
                          DocumentRequestRepository documentRequestRepository,
                          LoanRequestRepository loanRequestRepository,
                          AuthorizationRequestRepository authorizationRequestRepository,
                          AuthenticatedUserResolver authenticatedUserResolver,
                          UserRepository userRepository,
                          HRService hrService,
                          Clock clock) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.documentRequestRepository = documentRequestRepository;
        this.loanRequestRepository = loanRequestRepository;
        this.authorizationRequestRepository = authorizationRequestRepository;
        this.authenticatedUserResolver = authenticatedUserResolver;
        this.userRepository = userRepository;
        this.hrService = hrService;
        this.clock = clock;
    }

    /**
     * Builds the HR workbook from existing workflow records without changing request state.
     */
    public byte[] exportExcel(HrReportExportRequest request, Jwt jwt) {
        User requester = authenticatedUserResolver.require(jwt);
        if (requester.getRole() != TypeRole.HR_MANAGER) {
            throw new UnauthorizedException("Only HR_MANAGER can export HR reports.");
        }

        HrReportExportRequest normalizedRequest = normalizeRequest(request);
        Map<Long, EmployeeSnapshot> employeeLookup = buildEmployeeLookup();
        Map<String, String> actorLabelCache = new HashMap<>();

        List<HrReportRowDto> allRows = new ArrayList<>();
        Set<HrReportExportRequest.SourceType> sourceTypes = resolveSourceTypes(normalizedRequest.getSourceTypes());

        if (sourceTypes.contains(HrReportExportRequest.SourceType.LEAVE)) {
            allRows.addAll(normalizeLeaveRows(
                    leaveRequestRepository.findAllForReportExport(),
                    employeeLookup,
                    actorLabelCache
            ));
        }
        if (sourceTypes.contains(HrReportExportRequest.SourceType.DOCUMENT)) {
            allRows.addAll(normalizeDocumentRows(
                    documentRequestRepository.findAllForReportExport(),
                    employeeLookup,
                    actorLabelCache
            ));
        }
        if (sourceTypes.contains(HrReportExportRequest.SourceType.LOAN)) {
            allRows.addAll(normalizeLoanRows(
                    loanRequestRepository.findAllForReportExport(),
                    employeeLookup,
                    actorLabelCache
            ));
        }
        if (sourceTypes.contains(HrReportExportRequest.SourceType.AUTHORIZATION)) {
            allRows.addAll(normalizeAuthorizationRows(
                    authorizationRequestRepository.findAllForReportExport(),
                    employeeLookup,
                    actorLabelCache
            ));
        }

        List<HrReportRowDto> filteredRows = allRows.stream()
                .filter(row -> matchesFilters(row, normalizedRequest))
                .sorted((left, right) -> compareBySubmittedAt(right.submittedAt(), left.submittedAt()))
                .toList();

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            HrReportWorkbookStyles styles = HrReportExcelStyleHelper.createWorkbookStyles(workbook);
            createSummarySheet(workbook, filteredRows, allRows, normalizedRequest, styles);
            createLeafSheetIfIncluded(workbook, filteredRows, normalizedRequest, styles);
            createDocumentSheetIfIncluded(workbook, filteredRows, normalizedRequest, styles);
            createLoanSheetIfIncluded(workbook, filteredRows, normalizedRequest, styles);
            createAuthorizationSheetIfIncluded(workbook, filteredRows, normalizedRequest, styles);
            createPendingAgingSheetIfIncluded(workbook, filteredRows, normalizedRequest, styles);
            createDecisionActivitySheetIfIncluded(workbook, filteredRows, normalizedRequest, styles);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate HR reports Excel workbook", e);
        }
    }

    private HrReportExportRequest normalizeRequest(HrReportExportRequest request) {
        HrReportExportRequest normalized = new HrReportExportRequest();
        normalized.setSourceTypes(request != null ? request.getSourceTypes() : null);
        normalized.setDateBasis(request != null && request.getDateBasis() != null
                ? request.getDateBasis()
                : HrReportExportRequest.DateBasis.SUBMITTED);
        normalized.setDateFrom(request != null ? request.getDateFrom() : null);
        normalized.setDateTo(request != null ? request.getDateTo() : null);
        normalized.setStatus(request != null ? request.getStatus() : null);
        normalized.setDepartmentId(request != null ? request.getDepartmentId() : null);
        normalized.setTeamId(request != null ? request.getTeamId() : null);
        normalized.setStatusGroup(request != null ? request.getStatusGroup() : null);
        normalized.setIncludeSheets(request != null ? request.getIncludeSheets() : null);
        normalized.setTitle(request != null && request.getTitle() != null && !request.getTitle().isBlank()
                ? request.getTitle().trim()
                : DEFAULT_TITLE);
        return normalized;
    }

    private Set<HrReportExportRequest.SourceType> resolveSourceTypes(List<HrReportExportRequest.SourceType> requested) {
        if (requested == null || requested.isEmpty()) {
            return EnumSet.allOf(HrReportExportRequest.SourceType.class);
        }
        return requested.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(HrReportExportRequest.SourceType.class)));
    }

    private Map<Long, EmployeeSnapshot> buildEmployeeLookup() {
        Map<Long, EmployeeSnapshot> lookup = new HashMap<>();
        List<Map<String, Object>> rawUsers = hrService.getAllUsersWithDetails();
        for (Map<String, Object> userMap : rawUsers) {
            EmployeeSnapshot snapshot = EmployeeSnapshot.fromReportUserMap(userMap);
            if (snapshot.employeeId() != null) {
                lookup.put(snapshot.employeeId(), snapshot);
            }
        }
        return lookup;
    }

    private List<HrReportRowDto> normalizeLeaveRows(List<LeaveRequest> requests,
                                                    Map<Long, EmployeeSnapshot> lookup,
                                                    Map<String, String> actorLabelCache) {
        return requests.stream().map(request -> {
            EmployeeSnapshot employee = resolveEmployee(request.getUser(), lookup);
            String status = readableLeaveStatus(request.getStatus());
            HrReportExportRequest.StatusGroup statusGroup = deriveStatusGroup(status);
            LocalDateTime submittedAt = request.getRequestDate();
            LocalDateTime decisionAt = statusGroup == HrReportExportRequest.StatusGroup.PENDING
                    ? null
                    : firstNonNull(request.getApprovedAt(), request.getRejectedAt(), request.getCanceledAt());
            LocalDateTime closedAt = decisionAt;
            Long processingDays = deriveProcessingDays(submittedAt, decisionAt);
            Long pendingAgeDays = derivePendingAgeDays(submittedAt, statusGroup);
            String decisionActorId = firstNonBlank(request.getApprovedBy(), request.getRejectedBy(), request.getCanceledBy());
            String decisionActorLabel = resolveActorLabel(decisionActorId, actorLabelCache);
            String decisionReason = HrReportExportSanitizer.cleanWorkflowText(request.getDecisionReason());
            String hrNote = statusGroup == HrReportExportRequest.StatusGroup.PENDING ? null : decisionReason;
            String rejectionReason = statusGroup == HrReportExportRequest.StatusGroup.REJECTED ? decisionReason : null;
            String cancellationReason = statusGroup == HrReportExportRequest.StatusGroup.CANCELLED ? decisionReason : null;
            HrReportRowDto row = baseRow(
                    request.getId(),
                    HrReportExportRequest.SourceType.LEAVE,
                    "Leave",
                    readableLeaveType(request.getLeaveType() != null ? request.getLeaveType().name() : null),
                    status,
            readableLeaveStage(request),
                    statusGroup,
                    employee,
                    submittedAt,
                    decisionAt,
                    closedAt,
                    processingDays,
                    pendingAgeDays,
                    decisionActorId,
                    decisionActorLabel,
                    request.getReason(),
                    hrNote,
                    rejectionReason,
                    cancellationReason
            );
            row.setDays(request.getNumberOfDays());
            row.setLeaveType(readableLeaveType(request.getLeaveType() != null ? request.getLeaveType().name() : null));
            row.setStartDate(request.getStartDate());
            row.setEndDate(request.getEndDate());
            row.setDeductedDays(request.getNumberOfDays());
            row.setTlDecision(readableApprovalDecision(request.getTeamLeaderDecision()));
            row.setHrDecision(readableApprovalDecision(request.getHrDecision()));
            row.setMonthKey(buildMonthKey(submittedAt));
            row.setAgingBucket(buildAgingBucket(pendingAgeDays));
            row.setProcessingBucket(buildProcessingBucket(processingDays));
            row.setDepartmentGroup(HrReportExportSanitizer.safeGroup(HrReportExportSanitizer.cleanProfileValue(employee.departmentName()), "No department"));
            row.setTeamGroup(HrReportExportSanitizer.safeGroup(HrReportExportSanitizer.cleanProfileValue(employee.teamName()), "No team"));
            row.setIsPending(statusGroup == HrReportExportRequest.StatusGroup.PENDING);
            row.setIsApproved(statusGroup == HrReportExportRequest.StatusGroup.APPROVED);
            row.setIsRejected(statusGroup == HrReportExportRequest.StatusGroup.REJECTED);
            row.setIsCancelled(statusGroup == HrReportExportRequest.StatusGroup.CANCELLED);
            row.setIsStalePending(statusGroup == HrReportExportRequest.StatusGroup.PENDING && pendingAgeDays != null && pendingAgeDays >= 15);
            row.setNeedsHrFile(false);
            row.setRawSource(request);
            return row;
        }).toList();
    }

    private List<HrReportRowDto> normalizeDocumentRows(List<DocumentRequest> requests,
                                                       Map<Long, EmployeeSnapshot> lookup,
                                                       Map<String, String> actorLabelCache) {
        return requests.stream().map(request -> {
            EmployeeSnapshot employee = resolveEmployee(request.getUser(), lookup);
            String status = readableRequestStatus(request.getStatus());
            HrReportExportRequest.StatusGroup statusGroup = deriveStatusGroup(status);
            LocalDateTime submittedAt = request.getRequestedAt();
            LocalDateTime decisionAt = statusGroup == HrReportExportRequest.StatusGroup.PENDING ? null : request.getProcessedAt();
            LocalDateTime closedAt = decisionAt;
            Long processingDays = deriveProcessingDays(submittedAt, decisionAt);
            Long pendingAgeDays = derivePendingAgeDays(submittedAt, statusGroup);
            String decisionActorId = firstNonBlank(request.getApprovedBy(), request.getRejectedBy(), request.getCanceledBy());
            String decisionActorLabel = resolveActorLabel(decisionActorId, actorLabelCache);
            boolean waitingForHrFile = isDocumentWaitingForHrFile(request);
            boolean readyForDownload = isDocumentReadyForDownload(request);
            String fulfillmentStatus = readableDocumentFulfillmentStatus(request);
            String decisionNote = HrReportExportSanitizer.cleanWorkflowText(request.getHrNote());
            String rejectionReason = statusGroup == HrReportExportRequest.StatusGroup.REJECTED ? decisionNote : null;
            String cancellationReason = statusGroup == HrReportExportRequest.StatusGroup.CANCELLED ? decisionNote : null;
            HrReportRowDto row = baseRow(
                    request.getId(),
                    HrReportExportRequest.SourceType.DOCUMENT,
                    "Document",
                    readableDocumentType(request.getDocumentType() != null ? request.getDocumentType().name() : null),
                    status,
                    readableDocumentStage(fulfillmentStatus),
                    statusGroup,
                    employee,
                    submittedAt,
                    decisionAt,
                    closedAt,
                    processingDays,
                    pendingAgeDays,
                    decisionActorId,
                    decisionActorLabel,
                    HrReportExportSanitizer.cleanWorkflowText(request.getNotes()),
                    decisionNote,
                    rejectionReason,
                    cancellationReason
            );
            row.setDocumentType(readableDocumentType(request.getDocumentType() != null ? request.getDocumentType().name() : null));
            row.setFulfillmentStatus(fulfillmentStatus);
            row.setWaitingForHrFile(waitingForHrFile);
            row.setReadyForDownload(readyForDownload);
            row.setFileUploadedAt(request.getAttachmentUploadedAt());
            row.setMonthKey(buildMonthKey(submittedAt));
            row.setAgingBucket(buildAgingBucket(pendingAgeDays));
            row.setProcessingBucket(buildProcessingBucket(processingDays));
            row.setDepartmentGroup(HrReportExportSanitizer.safeGroup(HrReportExportSanitizer.cleanProfileValue(employee.departmentName()), "No department"));
            row.setTeamGroup(HrReportExportSanitizer.safeGroup(HrReportExportSanitizer.cleanProfileValue(employee.teamName()), "No team"));
            row.setIsPending(statusGroup == HrReportExportRequest.StatusGroup.PENDING);
            row.setIsApproved(statusGroup == HrReportExportRequest.StatusGroup.APPROVED);
            row.setIsRejected(statusGroup == HrReportExportRequest.StatusGroup.REJECTED);
            row.setIsCancelled(statusGroup == HrReportExportRequest.StatusGroup.CANCELLED);
            row.setIsStalePending(statusGroup == HrReportExportRequest.StatusGroup.PENDING && pendingAgeDays != null && pendingAgeDays >= 15);
            row.setNeedsHrFile(waitingForHrFile);
            row.setRawSource(request);
            return row;
        }).toList();
    }

    private List<HrReportRowDto> normalizeLoanRows(List<LoanRequest> requests,
                                                   Map<Long, EmployeeSnapshot> lookup,
                                                   Map<String, String> actorLabelCache) {
        return requests.stream().map(request -> {
            EmployeeSnapshot employee = resolveEmployee(request.getUser(), lookup);
            String status = readableRequestStatus(request.getStatus());
            HrReportExportRequest.StatusGroup statusGroup = deriveStatusGroup(status);
            LocalDateTime submittedAt = request.getRequestedAt();
            LocalDateTime decisionAt = statusGroup == HrReportExportRequest.StatusGroup.PENDING
                    ? null
                    : firstNonNull(request.getApprovedAt(), request.getRejectedAt(), request.getCanceledAt(), request.getProcessedAt());
            LocalDateTime closedAt = decisionAt;
            Long processingDays = deriveProcessingDays(submittedAt, decisionAt);
            Long pendingAgeDays = derivePendingAgeDays(submittedAt, statusGroup);
            String decisionActorId = firstNonBlank(request.getApprovedBy(), request.getRejectedBy(), request.getCanceledBy(), request.getMeetingScheduledBy());
            String decisionActorLabel = resolveActorLabel(decisionActorId, actorLabelCache);
            boolean needsHrFile = statusGroup == HrReportExportRequest.StatusGroup.APPROVED
                    && request.getAttachmentUploadedAt() == null;
            String meetingStatus = request.getMeetingAt() != null ? "Scheduled" : null;
            String decisionReason = HrReportExportSanitizer.cleanWorkflowText(request.getHrDecisionReason());
            String cancellationReason = statusGroup == HrReportExportRequest.StatusGroup.CANCELLED ? HrReportExportSanitizer.cleanWorkflowText(request.getCancellationReason()) : null;

            HrReportRowDto row = baseRow(
                    request.getId(),
                    HrReportExportRequest.SourceType.LOAN,
                    "Loan",
                    readableLoanType(request.getLoanType() != null ? request.getLoanType().name() : null),
                    status,
                    readableLoanStage(firstNonBlank(request.getHrDecisionStage(), meetingStatus)),
                    statusGroup,
                    employee,
                    submittedAt,
                    decisionAt,
                    closedAt,
                    processingDays,
                    pendingAgeDays,
                    decisionActorId,
                    decisionActorLabel,
                    HrReportExportSanitizer.cleanWorkflowText(request.getReason()),
                    HrReportExportSanitizer.cleanWorkflowText(request.getHrNote()),
                    statusGroup == HrReportExportRequest.StatusGroup.REJECTED ? decisionReason : null,
                    cancellationReason
            );
            row.setAmount(request.getAmount());
            row.setLoanType(readableLoanType(request.getLoanType() != null ? request.getLoanType().name() : null));
            row.setRequestedAmount(request.getAmount());
            row.setApprovedAmount(request.getApprovedAmount());
            row.setRepaymentMonths(request.getRepaymentMonths());
            row.setMonthlyInstallment(request.getMonthlyInstallment());
            row.setMeetingAt(request.getMeetingAt());
            row.setMeetingStatus(meetingStatus);
            row.setRiskScore(request.getRiskScore());
            row.setMonthKey(buildMonthKey(submittedAt));
            row.setAgingBucket(buildAgingBucket(pendingAgeDays));
            row.setProcessingBucket(buildProcessingBucket(processingDays));
            row.setDepartmentGroup(HrReportExportSanitizer.safeGroup(HrReportExportSanitizer.cleanProfileValue(employee.departmentName()), "No department"));
            row.setTeamGroup(HrReportExportSanitizer.safeGroup(HrReportExportSanitizer.cleanProfileValue(employee.teamName()), "No team"));
            row.setIsPending(statusGroup == HrReportExportRequest.StatusGroup.PENDING);
            row.setIsApproved(statusGroup == HrReportExportRequest.StatusGroup.APPROVED);
            row.setIsRejected(statusGroup == HrReportExportRequest.StatusGroup.REJECTED);
            row.setIsCancelled(statusGroup == HrReportExportRequest.StatusGroup.CANCELLED);
            row.setIsStalePending(statusGroup == HrReportExportRequest.StatusGroup.PENDING && pendingAgeDays != null && pendingAgeDays >= 15);
            row.setNeedsHrFile(needsHrFile);
            row.setRawSource(request);
            return row;
        }).toList();
    }

    private List<HrReportRowDto> normalizeAuthorizationRows(List<AuthorizationRequest> requests,
                                                            Map<Long, EmployeeSnapshot> lookup,
                                                            Map<String, String> actorLabelCache) {
        return requests.stream().map(request -> {
            EmployeeSnapshot employee = resolveEmployee(request.getUser(), lookup);
            String status = readableRequestStatus(request.getStatus());
            HrReportExportRequest.StatusGroup statusGroup = deriveStatusGroup(status);
            LocalDateTime submittedAt = request.getRequestedAt();
            LocalDateTime decisionAt = statusGroup == HrReportExportRequest.StatusGroup.PENDING ? null : firstNonNull(request.getCanceledAt(), request.getProcessedAt());
            LocalDateTime closedAt = decisionAt;
            Long processingDays = deriveProcessingDays(submittedAt, decisionAt);
            Long pendingAgeDays = derivePendingAgeDays(submittedAt, statusGroup);
            String decisionActorId = firstNonBlank(request.getApprovedBy(), request.getRejectedBy(), request.getCanceledBy());
            String decisionActorLabel = resolveActorLabel(decisionActorId, actorLabelCache);
            String decisionNote = HrReportExportSanitizer.cleanWorkflowText(request.getHrNote());
            String rejectionReason = statusGroup == HrReportExportRequest.StatusGroup.REJECTED ? decisionNote : null;
            String cancellationReason = statusGroup == HrReportExportRequest.StatusGroup.CANCELLED ? decisionNote : null;
            HrReportRowDto row = baseRow(
                    request.getId(),
                    HrReportExportRequest.SourceType.AUTHORIZATION,
                    "Authorization",
                    readableAuthorizationType(request.getAuthorizationType() != null ? request.getAuthorizationType().name() : null),
                    status,
                    readableAuthorizationStage(request.getAuthorizationType() != null ? request.getAuthorizationType().name() : null),
                    statusGroup,
                    employee,
                    submittedAt,
                    decisionAt,
                    closedAt,
                    processingDays,
                    pendingAgeDays,
                    decisionActorId,
                    decisionActorLabel,
                    HrReportExportSanitizer.cleanWorkflowText(request.getReason()),
                    decisionNote,
                    rejectionReason,
                    cancellationReason
            );
            row.setAuthorizationType(readableAuthorizationType(request.getAuthorizationType() != null ? request.getAuthorizationType().name() : null));
            row.setAbsenceDate(request.getAbsenceDate());
            row.setStartDate(request.getStartDate());
            row.setEndDate(request.getEndDate());
            row.setFromTime(request.getFromTime());
            row.setToTime(request.getToTime());
            row.setEquipmentType(request.getEquipmentType());
            row.setMonthKey(buildMonthKey(submittedAt));
            row.setAgingBucket(buildAgingBucket(pendingAgeDays));
            row.setProcessingBucket(buildProcessingBucket(processingDays));
            row.setDepartmentGroup(HrReportExportSanitizer.safeGroup(HrReportExportSanitizer.cleanProfileValue(employee.departmentName()), "No department"));
            row.setTeamGroup(HrReportExportSanitizer.safeGroup(HrReportExportSanitizer.cleanProfileValue(employee.teamName()), "No team"));
            row.setIsPending(statusGroup == HrReportExportRequest.StatusGroup.PENDING);
            row.setIsApproved(statusGroup == HrReportExportRequest.StatusGroup.APPROVED);
            row.setIsRejected(statusGroup == HrReportExportRequest.StatusGroup.REJECTED);
            row.setIsCancelled(statusGroup == HrReportExportRequest.StatusGroup.CANCELLED);
            row.setIsStalePending(statusGroup == HrReportExportRequest.StatusGroup.PENDING && pendingAgeDays != null && pendingAgeDays >= 15);
            row.setNeedsHrFile(false);
            row.setRawSource(request);
            return row;
        }).toList();
    }

    private EmployeeSnapshot resolveEmployee(User user, Map<Long, EmployeeSnapshot> lookup) {
        if (user == null) {
            return EmployeeSnapshot.empty();
        }
        EmployeeSnapshot direct = EmployeeSnapshot.fromUser(user);
        EmployeeSnapshot fallback = lookup.get(user.getId());
        return direct.merge(fallback);
    }

    private String resolveActorLabel(String actorId, Map<String, String> cache) {
        String normalized = actorId == null ? null : actorId.trim();
        if (normalized == null || normalized.isBlank()) {
            return null;
        }
        return cache.computeIfAbsent(normalized, key ->
                userRepository.findByKeycloakIdWithPersonAndTeamLeader(key)
                        .map(this::resolveActorDisplayName)
                        .orElse("HR user"));
    }

    private String resolveActorDisplayName(User user) {
        if (user == null) {
            return "HR user";
        }
        Person person = user.getPerson();
        if (person != null) {
            String first = person.getFirstName() != null ? person.getFirstName().trim() : "";
            String last = person.getLastName() != null ? person.getLastName().trim() : "";
            String full = (first + " " + last).trim();
            if (!full.isBlank()) {
                return full;
            }
        }
        if (user.getUsername() != null && !user.getUsername().isBlank()) {
            return user.getUsername();
        }
        return "HR user";
    }

    private boolean matchesFilters(HrReportRowDto row, HrReportExportRequest request) {
        if (row == null) {
            return false;
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            String expected = request.getStatus().trim();
            if (row.status() == null || !row.status().equalsIgnoreCase(expected)) {
                return false;
            }
        }
        if (request.getStatusGroup() != null && row.statusGroup() != request.getStatusGroup()) {
            return false;
        }
        if (request.getDepartmentId() != null && !Objects.equals(request.getDepartmentId(), row.departmentId())) {
            return false;
        }
        if (request.getTeamId() != null && !Objects.equals(request.getTeamId(), row.teamId())) {
            return false;
        }

        LocalDate basisDate = resolveBasisDate(row, request.getDateBasis());
        if (request.getDateFrom() != null && (basisDate == null || basisDate.isBefore(request.getDateFrom()))) {
            return false;
        }
        if (request.getDateTo() != null && (basisDate == null || basisDate.isAfter(request.getDateTo()))) {
            return false;
        }

        return true;
    }

    private LocalDate resolveBasisDate(HrReportRowDto row, HrReportExportRequest.DateBasis basis) {
        HrReportExportRequest.DateBasis effective = basis != null ? basis : HrReportExportRequest.DateBasis.SUBMITTED;
        return switch (effective) {
            case SUBMITTED -> row.submittedAt() != null ? row.submittedAt().toLocalDate() : null;
            case DECISION -> row.decisionAt() != null ? row.decisionAt().toLocalDate() : null;
            case CLOSED -> row.closedAt() != null ? row.closedAt().toLocalDate() : null;
        };
    }

    private HrReportExportRequest.StatusGroup deriveStatusGroup(String status) {
        if (status == null || status.isBlank()) {
            return HrReportExportRequest.StatusGroup.PENDING;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (normalized.contains("CANCELLED")) {
            return HrReportExportRequest.StatusGroup.CANCELLED;
        }
        if (normalized.contains("REJECTED")) {
            return HrReportExportRequest.StatusGroup.REJECTED;
        }
        if (normalized.contains("APPROVED") || normalized.contains("READY_FOR_DOWNLOAD")) {
            return HrReportExportRequest.StatusGroup.APPROVED;
        }
        return HrReportExportRequest.StatusGroup.PENDING;
    }

    private Long deriveProcessingDays(LocalDateTime submittedAt, LocalDateTime decisionAt) {
        if (submittedAt == null || decisionAt == null) {
            return null;
        }
        return Math.max(ChronoUnit.DAYS.between(submittedAt.toLocalDate(), decisionAt.toLocalDate()), 0L);
    }

    private Long derivePendingAgeDays(LocalDateTime submittedAt, HrReportExportRequest.StatusGroup statusGroup) {
        if (submittedAt == null || statusGroup != HrReportExportRequest.StatusGroup.PENDING) {
            return null;
        }
        LocalDate today = LocalDate.now(clock);
        return Math.max(ChronoUnit.DAYS.between(submittedAt.toLocalDate(), today), 0L);
    }

    private String buildMonthKey(LocalDateTime submittedAt) {
        if (submittedAt == null) {
            return null;
        }
        return YearMonth.from(submittedAt).toString();
    }

    private String buildAgingBucket(Long pendingAgeDays) {
        if (pendingAgeDays == null) {
            return null;
        }
        return buildBucket(pendingAgeDays);
    }

    private String buildProcessingBucket(Long processingDays) {
        if (processingDays == null) {
            return null;
        }
        return buildBucket(processingDays);
    }

    private String buildBucket(long days) {
        if (days <= 2) {
            return "0-2 days";
        }
        if (days <= 7) {
            return "3-7 days";
        }
        if (days <= 14) {
            return "8-14 days";
        }
        return "15+ days";
    }

    private String deriveLeaveStage(LeaveRequest request) {
        if (request.getStatus() == LeaveStatus.PENDING) {
            if (request.getTeamLeaderDecision() == ApprovalDecision.PENDING) {
                return "PENDING_TEAM_LEADER";
            }
            if (request.getTeamLeaderDecision() == ApprovalDecision.APPROVED
                    && request.getHrDecision() == ApprovalDecision.PENDING) {
                return "PENDING_HR";
            }
        }
        return request.getStatus() != null ? request.getStatus().name() : null;
    }

    private String documentFulfillmentStatus(DocumentRequest request) {
        if (request.getStatus() == RequestStatus.PENDING) {
            return "PENDING";
        }
        if (request.getStatus() == RequestStatus.REJECTED) {
            return "REJECTED";
        }
        if (request.getStatus() == RequestStatus.CANCELLED_BY_EMPLOYEE) {
            return "CANCELLED_BY_EMPLOYEE";
        }
        if (request.getStatus() != RequestStatus.APPROVED) {
            return request.getStatus() != null ? request.getStatus().name() : null;
        }
        if (request.getFulfillmentMode() == null || request.getFulfillmentMode().name().equals("GENERATED")) {
            return "READY_FOR_DOWNLOAD";
        }
        return isDocumentReadyForDownload(request) ? "READY_FOR_DOWNLOAD" : "APPROVED_WAITING_FOR_HR_FILE";
    }

    private boolean isDocumentWaitingForHrFile(DocumentRequest request) {
        return request.getStatus() == RequestStatus.APPROVED
                && request.getFulfillmentMode() != null
                && "UPLOADED".equals(request.getFulfillmentMode().name())
                && (request.getAttachmentStoragePath() == null || request.getAttachmentStoragePath().isBlank());
    }

    private boolean isDocumentReadyForDownload(DocumentRequest request) {
        if (request.getStatus() != RequestStatus.APPROVED) {
            return false;
        }
        if (request.getFulfillmentMode() == null || "GENERATED".equals(request.getFulfillmentMode().name())) {
            return request.getVerificationToken() != null && !request.getVerificationToken().isBlank();
        }
        return request.getAttachmentStoragePath() != null && !request.getAttachmentStoragePath().isBlank();
    }

    private String readableStatusLabel(String status) {
        if (HrReportExportSanitizer.isSentinelValue(status)) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "PENDING" -> "Pending";
            case "APPROVED" -> "Approved";
            case "REJECTED" -> "Rejected";
            case "CANCELLED", "CANCELLED_BY_EMPLOYEE", "CANCELLED_AFTER_MEETING" -> "Cancelled";
            case "APPROVED_WAITING_FOR_HR_FILE" -> "Approved - needs final file";
            case "READY_FOR_DOWNLOAD" -> "Approved - ready for download";
            default -> formatEnum(normalized);
        };
    }

    private String readableStageLabel(String stage) {
        if (HrReportExportSanitizer.isSentinelValue(stage)) {
            return null;
        }
        String normalized = stage.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "PENDING_TEAM_LEADER" -> "Pending team leader";
            case "PENDING_HR" -> "Pending HR";
            case "APPROVED_WAITING_FOR_HR_FILE" -> "Approved - needs final file";
            case "CANCELLED_BY_EMPLOYEE" -> "Cancelled by employee";
            case "CANCELLED_AFTER_MEETING" -> "Cancelled after meeting";
            case "APPROVED" -> "Approved";
            case "REJECTED" -> "Rejected";
            default -> formatEnum(normalized);
        };
    }

    private String readableLeaveStatus(LeaveStatus status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case PENDING -> "Pending";
            case APPROVED -> "Approved";
            case REJECTED -> "Rejected";
            case CANCELLED_BY_EMPLOYEE -> "Cancelled";
        };
    }

    private String readableRequestStatus(RequestStatus status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case PENDING -> "Pending";
            case APPROVED -> "Approved";
            case REJECTED -> "Rejected";
            case CANCELLED_BY_EMPLOYEE, CANCELLED_AFTER_MEETING -> "Cancelled";
            case SYSTEM_REJECTED -> "Rejected";
        };
    }

    private String readableApprovalDecision(ApprovalDecision decision) {
        if (decision == null) {
            return null;
        }
        return switch (decision) {
            case PENDING -> "Pending";
            case APPROVED -> "Approved";
            case REJECTED -> "Rejected";
        };
    }

    private String readableLeaveType(String value) {
        return HrReportExportSanitizer.cleanTextValue(formatEnum(value));
    }

    private String readableDocumentType(String value) {
        return HrReportExportSanitizer.cleanTextValue(formatEnum(value));
    }

    private String readableLoanType(String value) {
        return HrReportExportSanitizer.cleanTextValue(formatEnum(value));
    }

    private String readableAuthorizationType(String value) {
        return HrReportExportSanitizer.cleanTextValue(formatEnum(value));
    }

    private String readableDocumentStage(String fulfillmentStatus) {
        return readableStageLabel(fulfillmentStatus);
    }

    private String readableLoanStage(String stage) {
        return readableStageLabel(stage);
    }

    private String readableAuthorizationStage(String stage) {
        return readableStageLabel(stage);
    }

    private String readableDocumentFulfillmentStatus(DocumentRequest request) {
        String raw = documentFulfillmentStatus(request);
        return readableStageLabel(raw);
    }

    private String readableLeaveStage(LeaveRequest request) {
        if (request == null) {
            return null;
        }
        return readableStageLabel(deriveLeaveStage(request));
    }

    private String formatEnum(String enumName) {
        if (HrReportExportSanitizer.isSentinelValue(enumName)) {
            return null;
        }
        String[] parts = enumName.split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return builder.toString();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private LocalDateTime firstNonNull(LocalDateTime... values) {
        if (values == null) {
            return null;
        }
        for (LocalDateTime value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private int compareBySubmittedAt(LocalDateTime left, LocalDateTime right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return left.compareTo(right);
    }

    private void createSummarySheet(Workbook workbook, List<HrReportRowDto> rows, List<HrReportRowDto> allRows,
                                    HrReportExportRequest request, HrReportWorkbookStyles styles) {
        Sheet sheet = workbook.createSheet("Summary");
        int rowIndex = 0;

        rowIndex = writeTitleSection(sheet, rowIndex, request, styles);
        rowIndex = writeReportScopeSection(sheet, rowIndex, request, styles);
        rowIndex = writeAppliedFiltersSection(sheet, rowIndex, request, allRows, styles);
        rowIndex = writeMetricsSection(sheet, rowIndex, rows, styles);
        rowIndex = writeStatusBreakdownSection(sheet, rowIndex, rows, styles);
        rowIndex = writeSourceBreakdownSection(sheet, rowIndex, rows, styles);
        rowIndex = writeDepartmentWorkloadSection(sheet, rowIndex, rows, styles);
        rowIndex = writePendingAgingSummarySection(sheet, rowIndex, rows, styles);
        rowIndex = writeAttentionPreviewSection(sheet, rowIndex, rows, styles);

        setSummaryColumnWidths(sheet);
    }

    private int writeTitleSection(Sheet sheet, int startRow, HrReportExportRequest request, HrReportWorkbookStyles styles) {
        Row titleRow = sheet.createRow(startRow++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(request.getTitle() != null ? request.getTitle() : DEFAULT_TITLE);
        titleCell.setCellStyle(styles.titleStyle());
        sheet.addMergedRegion(new CellRangeAddress(titleRow.getRowNum(), titleRow.getRowNum(), 0, 5));

        Row subtitleRow = sheet.createRow(startRow++);
        Cell subtitleCell = subtitleRow.createCell(0);
        subtitleCell.setCellValue("Detailed employee/request rows are available in the Leaves/Documents/Loans/Authorizations sheets.");
        subtitleCell.setCellStyle(styles.subtitleStyle());
        sheet.addMergedRegion(new CellRangeAddress(subtitleRow.getRowNum(), subtitleRow.getRowNum(), 0, 5));

        Row generatedRow = sheet.createRow(startRow++);
        generatedRow.createCell(0).setCellValue("Generated at");
        generatedRow.getCell(0).setCellStyle(styles.sectionLabelStyle());
        generatedRow.createCell(1).setCellValue(HrReportExportLabels.formatDateTime(LocalDateTime.now(clock)));
        generatedRow.getCell(1).setCellStyle(styles.textStyle());
        return startRow + 1;
    }

    private int writeReportScopeSection(Sheet sheet, int startRow, HrReportExportRequest request, HrReportWorkbookStyles styles) {
        List<List<Object>> values = List.<List<Object>>of(
                List.<Object>of("Source", resolveSourceScopeLabel(request))
        );
        return writeLabeledPairsSection(sheet, startRow, "Report scope", values, styles);
    }

    private int writeAppliedFiltersSection(Sheet sheet, int startRow, HrReportExportRequest request,
                                           List<HrReportRowDto> allRows, HrReportWorkbookStyles styles) {
        List<List<Object>> values = List.<List<Object>>of(
                List.<Object>of("Date basis", resolveDateBasisLabel(request)),
                List.<Object>of("Period", resolvePeriodLabel(request)),
                List.<Object>of("Status", resolveStatusLabel(request)),
                List.<Object>of("Department", resolveDepartmentLabel(request, allRows)),
                List.<Object>of("Team", resolveTeamLabel(request, allRows))
        );
        return writeLabeledPairsSection(sheet, startRow, "Applied filters", values, styles);
    }

    private int writeMetricsSection(Sheet sheet, int startRow, List<HrReportRowDto> rows, HrReportWorkbookStyles styles) {
        List<List<Object>> metrics = List.<List<Object>>of(
                List.<Object>of("Total requests", rows.size()),
                List.<Object>of("Pending", count(rows, HrReportRowDto::isPending)),
                List.<Object>of("Approved", count(rows, HrReportRowDto::isApproved)),
                List.<Object>of("Rejected", count(rows, HrReportRowDto::isRejected)),
                List.<Object>of("Cancelled", count(rows, HrReportRowDto::isCancelled)),
                List.<Object>of("Stale pending", count(rows, HrReportRowDto::isStalePending)),
                List.<Object>of("Needs HR file", count(rows, HrReportRowDto::needsHrFile)),
                List.<Object>of("Average processing time", formatAverageDays(rows.stream().map(HrReportRowDto::processingDays).filter(Objects::nonNull).toList())),
                List.<Object>of("Average pending age", formatAverageDays(rows.stream().map(HrReportRowDto::pendingAgeDays).filter(Objects::nonNull).toList()))
        );
        return writeTableSection(sheet, startRow, "Key metrics", List.of("Metric", "Value"), metrics, styles, 0, 1);
    }

    private int writeStatusBreakdownSection(Sheet sheet, int startRow, List<HrReportRowDto> rows, HrReportWorkbookStyles styles) {
        Map<HrReportExportRequest.StatusGroup, Long> counts = rows.stream()
                .collect(Collectors.groupingBy(row -> row.statusGroup() != null ? row.statusGroup() : HrReportExportRequest.StatusGroup.PENDING,
                        LinkedHashMap::new, Collectors.counting()));
        List<List<Object>> tableRows = List.<List<Object>>of(
                List.<Object>of("Pending", counts.getOrDefault(HrReportExportRequest.StatusGroup.PENDING, 0L)),
                List.<Object>of("Approved", counts.getOrDefault(HrReportExportRequest.StatusGroup.APPROVED, 0L)),
                List.<Object>of("Rejected", counts.getOrDefault(HrReportExportRequest.StatusGroup.REJECTED, 0L)),
                List.<Object>of("Cancelled", counts.getOrDefault(HrReportExportRequest.StatusGroup.CANCELLED, 0L))
        );
        return writeTableSection(sheet, startRow, "Status breakdown", List.of("Status", "Count"), tableRows, styles, 0, 1);
    }

    private int writeSourceBreakdownSection(Sheet sheet, int startRow, List<HrReportRowDto> rows, HrReportWorkbookStyles styles) {
        Map<HrReportExportRequest.SourceType, Long> counts = rows.stream()
                .collect(Collectors.groupingBy(row -> row.sourceType() != null ? row.sourceType() : HrReportExportRequest.SourceType.LEAVE,
                        LinkedHashMap::new, Collectors.counting()));
        List<List<Object>> tableRows = List.<List<Object>>of(
                List.<Object>of("Leaves", counts.getOrDefault(HrReportExportRequest.SourceType.LEAVE, 0L)),
                List.<Object>of("Documents", counts.getOrDefault(HrReportExportRequest.SourceType.DOCUMENT, 0L)),
                List.<Object>of("Loans", counts.getOrDefault(HrReportExportRequest.SourceType.LOAN, 0L)),
                List.<Object>of("Authorizations", counts.getOrDefault(HrReportExportRequest.SourceType.AUTHORIZATION, 0L))
        );
        return writeTableSection(sheet, startRow, "Source type breakdown", List.of("Source type", "Count"), tableRows, styles, 0, 1);
    }

    private int writeDepartmentWorkloadSection(Sheet sheet, int startRow, List<HrReportRowDto> rows, HrReportWorkbookStyles styles) {
        Map<String, List<HrReportRowDto>> byDepartment = rows.stream()
                .collect(Collectors.groupingBy(row -> HrReportExportSanitizer.safeGroup(row.departmentGroup(), "No department"), LinkedHashMap::new, Collectors.toList()));
        List<List<Object>> tableRows = byDepartment.entrySet().stream()
                .sorted((left, right) -> Long.compare(right.getValue().size(), left.getValue().size()))
                .limit(10)
                .map(entry -> {
                    List<HrReportRowDto> deptRows = entry.getValue();
                    long total = deptRows.size();
                    long pending = count(deptRows, HrReportRowDto::isPending);
                    long approved = count(deptRows, HrReportRowDto::isApproved);
                    long rejectedCancelled = count(deptRows, row -> Boolean.TRUE.equals(row.isRejected()) || Boolean.TRUE.equals(row.isCancelled()));
                    return List.<Object>of(entry.getKey(), total, pending, approved, rejectedCancelled);
                })
                .toList();
        return writeTableSection(sheet, startRow, "Department workload top 10",
                List.of("Department", "Total", "Pending", "Approved", "Rejected/Cancelled"), tableRows, styles, 0, 1);
    }

    private int writePendingAgingSummarySection(Sheet sheet, int startRow, List<HrReportRowDto> rows, HrReportWorkbookStyles styles) {
        List<HrReportRowDto> pending = rows.stream()
                .filter(row -> Boolean.TRUE.equals(row.isPending()))
                .toList();
        Map<String, Long> counts = pending.stream()
                .collect(Collectors.groupingBy(row -> HrReportExportSanitizer.safeGroup(row.agingBucket(), "Unknown"), LinkedHashMap::new, Collectors.counting()));
        List<List<Object>> tableRows = List.<List<Object>>of(
                List.<Object>of("0-2 days", counts.getOrDefault("0-2 days", 0L)),
                List.<Object>of("3-7 days", counts.getOrDefault("3-7 days", 0L)),
                List.<Object>of("8-14 days", counts.getOrDefault("8-14 days", 0L)),
                List.<Object>of("15+ days", counts.getOrDefault("15+ days", 0L))
        );
        return writeTableSection(sheet, startRow, "Pending aging", List.of("Bucket", "Count"), tableRows, styles, 0, 1);
    }

    private int writeAttentionPreviewSection(Sheet sheet, int startRow, List<HrReportRowDto> rows, HrReportWorkbookStyles styles) {
        List<List<Object>> tableRows = rows.stream()
                .filter(row -> Boolean.TRUE.equals(row.isPending()) || Boolean.TRUE.equals(row.isStalePending()))
                .sorted((left, right) -> HrReportExportLabels.compareLongs(right.pendingAgeDays(), left.pendingAgeDays()))
                .limit(10)
                .map(row -> List.<Object>of(
                        HrReportExportLabels.safe(row.employeeName()),
                        HrReportExportLabels.safe(row.requestSubtype()),
                        HrReportExportSanitizer.safeGroup(row.departmentGroup(), "No department"),
                        HrReportExportSanitizer.safeGroup(row.teamGroup(), "No team"),
                        row.pendingAgeDays() != null ? row.pendingAgeDays() + " days" : "",
                        HrReportExportLabels.formatDateTime(row.submittedAt())
                ))
                .toList();
        return writeTableSection(sheet, startRow, "Attention items / stale pending preview",
                List.of("Employee name", "Request type", "Department", "Team", "Pending age", "Submitted date"),
                tableRows, styles, 0, 5);
    }

    private int writeLabeledPairsSection(Sheet sheet, int startRow, String title, List<List<Object>> rows,
                                         HrReportWorkbookStyles styles) {
        return writeTableSection(sheet, startRow, title, List.of("Label", "Value"), rows, styles, 0, 1);
    }

    private int writeTableSection(Sheet sheet, int startRow, String title, List<String> headers,
                                  List<List<Object>> rows, HrReportWorkbookStyles styles, int labelColumn, int valueColumn) {
        int rowIndex = startRow;
        Row sectionRow = sheet.createRow(rowIndex++);
        Cell sectionCell = sectionRow.createCell(0);
        sectionCell.setCellValue(title);
        sectionCell.setCellStyle(styles.sectionHeaderStyle());
        sheet.addMergedRegion(new CellRangeAddress(sectionRow.getRowNum(), sectionRow.getRowNum(), 0, Math.max(1, headers.size() - 1)));

        Row headerRow = sheet.createRow(rowIndex++);
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers.get(i));
            cell.setCellStyle(styles.tableHeaderStyle());
        }

        if (rows.isEmpty()) {
            Row emptyRow = sheet.createRow(rowIndex++);
            Cell emptyCell = emptyRow.createCell(0);
            emptyCell.setCellValue("No rows in the selected scope.");
            emptyCell.setCellStyle(styles.textStyle());
            sheet.addMergedRegion(new CellRangeAddress(emptyRow.getRowNum(), emptyRow.getRowNum(), 0, Math.max(1, headers.size() - 1)));
        } else {
            for (List<Object> rowValues : rows) {
                Row row = sheet.createRow(rowIndex++);
                writeObjectRow(row, rowValues, styles, Set.of());
            }
        }

        return rowIndex + 1;
    }

    private void createLeafSheetIfIncluded(Workbook workbook, List<HrReportRowDto> rows, HrReportExportRequest request, HrReportWorkbookStyles styles) {
        if (!shouldIncludeSheet(request, "LEAVES")) {
            return;
        }
        List<HrReportRowDto> data = rows.stream().filter(r -> r.sourceType() == HrReportExportRequest.SourceType.LEAVE).toList();
        writeDetailedSheet(workbook, "Leaves", data, List.of(
                "Employee name", "Username", "Email", "Hire date", "Department", "Team", "Job title", "Role",
                "Request category", "Request type", "Status", "Stage", "Submitted date", "Decision date", "Processing days",
                "Decision actor", "Employee reason", "HR note", "Rejection reason", "Cancellation reason",
                "Leave type", "Start date", "End date", "Deducted working days", "TL decision", "HR decision"
        ), List.of(
                HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.DATE, HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT,
                HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT,
                HrReportDetailColumnKind.DATETIME, HrReportDetailColumnKind.DATETIME, HrReportDetailColumnKind.OPTIONAL_NUMBER, HrReportDetailColumnKind.TEXT,
                HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT,
                HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.DATE, HrReportDetailColumnKind.DATE, HrReportDetailColumnKind.NUMBER, HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT
        ), (row, reportRow) -> {
            int c = 0;
            writeTextCell(row, c++, reportRow.employeeName(), styles, false);
            writeTextCell(row, c++, reportRow.username(), styles, false);
            writeTextCell(row, c++, reportRow.email(), styles, false);
            writeDateCell(row, c++, reportRow.hireDate(), styles);
            writeTextCell(row, c++, reportRow.departmentName(), styles, false);
            writeTextCell(row, c++, reportRow.teamName(), styles, false);
            writeTextCell(row, c++, reportRow.jobTitle(), styles, false);
            writeTextCell(row, c++, reportRow.role(), styles, false);
            writeTextCell(row, c++, HrReportExportLabels.displaySourceType(reportRow.sourceType()), styles, false);
            writeTextCell(row, c++, reportRow.requestSubtype(), styles, false);
            writeTextCell(row, c++, reportRow.status(), styles, false);
            writeTextCell(row, c++, reportRow.stage(), styles, false);
            writeDateTimeCell(row, c++, reportRow.submittedAt(), styles);
            writeDateTimeCell(row, c++, reportRow.decisionAt(), styles);
            writeOptionalNumberCell(row, c++, reportRow.processingDays(), reportRow.decisionAt() != null, styles);
            writeTextCell(row, c++, reportRow.decisionActorLabel(), styles, false);
            writeTextCell(row, c++, reportRow.reason(), styles, true);
            writeTextCell(row, c++, reportRow.hrNote(), styles, true);
            writeTextCell(row, c++, reportRow.rejectionReason(), styles, true);
            writeTextCell(row, c++, reportRow.cancellationReason(), styles, true);
            writeTextCell(row, c++, reportRow.leaveType(), styles, false);
            writeDateCell(row, c++, reportRow.startDate(), styles);
            writeDateCell(row, c++, reportRow.endDate(), styles);
            writeNumberCell(row, c++, reportRow.deductedDays(), styles);
            writeTextCell(row, c++, reportRow.tlDecision(), styles, false);
            writeTextCell(row, c++, reportRow.hrDecision(), styles, false);
        }, styles, Set.of(16, 17, 18, 19));
    }

    private void createDocumentSheetIfIncluded(Workbook workbook, List<HrReportRowDto> rows, HrReportExportRequest request, HrReportWorkbookStyles styles) {
        if (!shouldIncludeSheet(request, "DOCUMENTS")) {
            return;
        }
        List<HrReportRowDto> data = rows.stream().filter(r -> r.sourceType() == HrReportExportRequest.SourceType.DOCUMENT).toList();
        writeDetailedSheet(workbook, "Documents", data, List.of(
                "Employee name", "Username", "Email", "Hire date", "Department", "Team", "Job title", "Role",
                "Request category", "Request type", "Status", "Stage", "Submitted date", "Decision date", "Processing days",
                "Decision actor", "Employee reason", "HR note", "Rejection reason", "Cancellation reason",
                "Document type", "Fulfillment status", "Waiting for HR file", "Ready for download"
        ), List.of(
                HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.DATE, HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT,
                HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT,
                HrReportDetailColumnKind.DATETIME, HrReportDetailColumnKind.DATETIME, HrReportDetailColumnKind.OPTIONAL_NUMBER, HrReportDetailColumnKind.TEXT,
                HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT,
                HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT
        ), (row, reportRow) -> {
            int c = 0;
            writeTextCell(row, c++, reportRow.employeeName(), styles, false);
            writeTextCell(row, c++, reportRow.username(), styles, false);
            writeTextCell(row, c++, reportRow.email(), styles, false);
            writeDateCell(row, c++, reportRow.hireDate(), styles);
            writeTextCell(row, c++, reportRow.departmentName(), styles, false);
            writeTextCell(row, c++, reportRow.teamName(), styles, false);
            writeTextCell(row, c++, reportRow.jobTitle(), styles, false);
            writeTextCell(row, c++, reportRow.role(), styles, false);
            writeTextCell(row, c++, HrReportExportLabels.displaySourceType(reportRow.sourceType()), styles, false);
            writeTextCell(row, c++, reportRow.requestSubtype(), styles, false);
            writeTextCell(row, c++, reportRow.status(), styles, false);
            writeTextCell(row, c++, reportRow.stage(), styles, false);
            writeDateTimeCell(row, c++, reportRow.submittedAt(), styles);
            writeDateTimeCell(row, c++, reportRow.decisionAt(), styles);
            writeOptionalNumberCell(row, c++, reportRow.processingDays(), reportRow.decisionAt() != null, styles);
            writeTextCell(row, c++, reportRow.decisionActorLabel(), styles, false);
            writeTextCell(row, c++, reportRow.reason(), styles, true);
            writeTextCell(row, c++, reportRow.hrNote(), styles, true);
            writeTextCell(row, c++, reportRow.rejectionReason(), styles, true);
            writeTextCell(row, c++, reportRow.cancellationReason(), styles, true);
            writeTextCell(row, c++, reportRow.documentType(), styles, false);
            writeTextCell(row, c++, reportRow.fulfillmentStatus(), styles, false);
            writeTextCell(row, c++, HrReportExportLabels.safeBoolean(reportRow.waitingForHrFile()), styles, false);
            writeTextCell(row, c++, HrReportExportLabels.safeBoolean(reportRow.readyForDownload()), styles, false);
        }, styles, Set.of(16, 17, 18, 19));
    }

    private void createLoanSheetIfIncluded(Workbook workbook, List<HrReportRowDto> rows, HrReportExportRequest request, HrReportWorkbookStyles styles) {
        if (!shouldIncludeSheet(request, "LOANS")) {
            return;
        }
        List<HrReportRowDto> data = rows.stream().filter(r -> r.sourceType() == HrReportExportRequest.SourceType.LOAN).toList();
        writeDetailedSheet(workbook, "Loans", data, List.of(
                "Employee name", "Username", "Email", "Hire date", "Department", "Team", "Job title", "Role",
                "Request category", "Request type", "Status", "Stage", "Submitted date", "Decision date", "Processing days",
                "Decision actor", "Employee reason", "HR note", "Rejection reason", "Cancellation reason",
                "Loan type", "Requested amount", "Approved amount", "Repayment months", "Monthly installment",
                "Meeting date", "Meeting status", "Risk score"
        ), List.of(
                HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.DATE, HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT,
                HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT,
                HrReportDetailColumnKind.DATETIME, HrReportDetailColumnKind.DATETIME, HrReportDetailColumnKind.OPTIONAL_NUMBER, HrReportDetailColumnKind.TEXT,
                HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT,
                HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.NUMBER, HrReportDetailColumnKind.OPTIONAL_NUMBER, HrReportDetailColumnKind.NUMBER,
                HrReportDetailColumnKind.NUMBER, HrReportDetailColumnKind.DATETIME, HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.OPTIONAL_NUMBER
        ), (row, reportRow) -> {
            int c = 0;
            writeTextCell(row, c++, reportRow.employeeName(), styles, false);
            writeTextCell(row, c++, reportRow.username(), styles, false);
            writeTextCell(row, c++, reportRow.email(), styles, false);
            writeDateCell(row, c++, reportRow.hireDate(), styles);
            writeTextCell(row, c++, reportRow.departmentName(), styles, false);
            writeTextCell(row, c++, reportRow.teamName(), styles, false);
            writeTextCell(row, c++, reportRow.jobTitle(), styles, false);
            writeTextCell(row, c++, reportRow.role(), styles, false);
            writeTextCell(row, c++, HrReportExportLabels.displaySourceType(reportRow.sourceType()), styles, false);
            writeTextCell(row, c++, reportRow.requestSubtype(), styles, false);
            writeTextCell(row, c++, reportRow.status(), styles, false);
            writeTextCell(row, c++, reportRow.stage(), styles, false);
            writeDateTimeCell(row, c++, reportRow.submittedAt(), styles);
            writeDateTimeCell(row, c++, reportRow.decisionAt(), styles);
            writeOptionalNumberCell(row, c++, reportRow.processingDays(), reportRow.decisionAt() != null, styles);
            writeTextCell(row, c++, reportRow.decisionActorLabel(), styles, false);
            writeTextCell(row, c++, reportRow.reason(), styles, true);
            writeTextCell(row, c++, reportRow.hrNote(), styles, true);
            writeTextCell(row, c++, reportRow.rejectionReason(), styles, true);
            writeTextCell(row, c++, reportRow.cancellationReason(), styles, true);
            writeTextCell(row, c++, reportRow.loanType(), styles, false);
            writeNumberCell(row, c++, reportRow.requestedAmount(), styles);
            writeOptionalNumberCell(row, c++, reportRow.approvedAmount(), Boolean.TRUE.equals(reportRow.isApproved()), styles);
            writeNumberCell(row, c++, reportRow.repaymentMonths(), styles);
            writeNumberCell(row, c++, reportRow.monthlyInstallment(), styles);
            writeDateTimeCell(row, c++, reportRow.meetingAt(), styles);
            writeTextCell(row, c++, reportRow.meetingStatus(), styles, false);
            writeOptionalNumberCell(row, c++, reportRow.riskScore(), !Boolean.TRUE.equals(reportRow.isPending()) && reportRow.riskScore() != null, styles);
        }, styles, Set.of(16, 17, 18, 19));
    }

    private void createAuthorizationSheetIfIncluded(Workbook workbook, List<HrReportRowDto> rows, HrReportExportRequest request, HrReportWorkbookStyles styles) {
        if (!shouldIncludeSheet(request, "AUTHORIZATIONS")) {
            return;
        }
        List<HrReportRowDto> data = rows.stream().filter(r -> r.sourceType() == HrReportExportRequest.SourceType.AUTHORIZATION).toList();
        writeDetailedSheet(workbook, "Authorizations", data, List.of(
                "Employee name", "Username", "Email", "Hire date", "Department", "Team", "Job title", "Role",
                "Request category", "Request type", "Status", "Stage", "Submitted date", "Decision date", "Processing days",
                "Decision actor", "Employee reason", "HR note", "Rejection reason", "Cancellation reason",
                "Authorization type", "Absence date", "Start date", "End date", "From time", "To time", "Equipment type"
        ), List.of(
                HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.DATE, HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT,
                HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT,
                HrReportDetailColumnKind.DATETIME, HrReportDetailColumnKind.DATETIME, HrReportDetailColumnKind.OPTIONAL_NUMBER, HrReportDetailColumnKind.TEXT,
                HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT,
                HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.DATE, HrReportDetailColumnKind.DATE, HrReportDetailColumnKind.DATE,
                HrReportDetailColumnKind.TIME, HrReportDetailColumnKind.TIME, HrReportDetailColumnKind.TEXT
        ), (row, reportRow) -> {
            int c = 0;
            writeTextCell(row, c++, reportRow.employeeName(), styles, false);
            writeTextCell(row, c++, reportRow.username(), styles, false);
            writeTextCell(row, c++, reportRow.email(), styles, false);
            writeDateCell(row, c++, reportRow.hireDate(), styles);
            writeTextCell(row, c++, reportRow.departmentName(), styles, false);
            writeTextCell(row, c++, reportRow.teamName(), styles, false);
            writeTextCell(row, c++, reportRow.jobTitle(), styles, false);
            writeTextCell(row, c++, reportRow.role(), styles, false);
            writeTextCell(row, c++, HrReportExportLabels.displaySourceType(reportRow.sourceType()), styles, false);
            writeTextCell(row, c++, reportRow.requestSubtype(), styles, false);
            writeTextCell(row, c++, reportRow.status(), styles, false);
            writeTextCell(row, c++, reportRow.stage(), styles, false);
            writeDateTimeCell(row, c++, reportRow.submittedAt(), styles);
            writeDateTimeCell(row, c++, reportRow.decisionAt(), styles);
            writeOptionalNumberCell(row, c++, reportRow.processingDays(), reportRow.decisionAt() != null, styles);
            writeTextCell(row, c++, reportRow.decisionActorLabel(), styles, false);
            writeTextCell(row, c++, reportRow.reason(), styles, true);
            writeTextCell(row, c++, reportRow.hrNote(), styles, true);
            writeTextCell(row, c++, reportRow.rejectionReason(), styles, true);
            writeTextCell(row, c++, reportRow.cancellationReason(), styles, true);
            writeTextCell(row, c++, reportRow.authorizationType(), styles, false);
            writeDateCell(row, c++, reportRow.absenceDate(), styles);
            writeDateCell(row, c++, reportRow.startDate(), styles);
            writeDateCell(row, c++, reportRow.endDate(), styles);
            writeTimeCell(row, c++, reportRow.fromTime(), styles);
            writeTimeCell(row, c++, reportRow.toTime(), styles);
            writeTextCell(row, c++, reportRow.equipmentType(), styles, false);
        }, styles, Set.of(16, 17, 18, 19));
    }

    private void createPendingAgingSheetIfIncluded(Workbook workbook, List<HrReportRowDto> rows, HrReportExportRequest request, HrReportWorkbookStyles styles) {
        if (!shouldIncludeSheet(request, "PENDING_AGING")) {
            return;
        }
        List<HrReportRowDto> data = rows.stream()
                .filter(row -> row.isPending() != null && row.isPending())
                .sorted((left, right) -> HrReportExportLabels.compareLongs(right.pendingAgeDays(), left.pendingAgeDays()))
                .toList();
        writeDetailedSheet(workbook, "Pending Aging", data, List.of(
                "Employee name", "Username", "Department", "Team", "Request category", "Request type", "Status",
                "Stage", "Submitted date", "Pending age days", "Aging bucket", "Decision actor", "Employee reason", "HR note"
        ), List.of(
                HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT,
                HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.DATETIME, HrReportDetailColumnKind.NUMBER, HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT,
                HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT
        ), (row, reportRow) -> {
            int c = 0;
            writeTextCell(row, c++, reportRow.employeeName(), styles, false);
            writeTextCell(row, c++, reportRow.username(), styles, false);
            writeTextCell(row, c++, reportRow.departmentName(), styles, false);
            writeTextCell(row, c++, reportRow.teamName(), styles, false);
            writeTextCell(row, c++, HrReportExportLabels.displaySourceType(reportRow.sourceType()), styles, false);
            writeTextCell(row, c++, reportRow.requestSubtype(), styles, false);
            writeTextCell(row, c++, reportRow.status(), styles, false);
            writeTextCell(row, c++, reportRow.stage(), styles, false);
            writeDateTimeCell(row, c++, reportRow.submittedAt(), styles);
            writeNumberCell(row, c++, reportRow.pendingAgeDays(), styles);
            writeTextCell(row, c++, reportRow.agingBucket(), styles, false);
            writeTextCell(row, c++, reportRow.decisionActorLabel(), styles, false);
            writeTextCell(row, c++, reportRow.reason(), styles, true);
            writeTextCell(row, c++, reportRow.hrNote(), styles, true);
        }, styles, Set.of(12, 13));
    }

    private void createDecisionActivitySheetIfIncluded(Workbook workbook, List<HrReportRowDto> rows, HrReportExportRequest request, HrReportWorkbookStyles styles) {
        if (!shouldIncludeSheet(request, "DECISION_ACTIVITY")) {
            return;
        }
        List<HrReportRowDto> data = rows.stream()
                .filter(row -> row.decisionAt() != null)
                .sorted((left, right) -> compareBySubmittedAt(right.decisionAt(), left.decisionAt()))
                .toList();
        writeDetailedSheet(workbook, "Decision Activity", data, List.of(
                "Decision date", "Decision actor", "Source type", "Request type", "Request ID", "Employee name",
                "Status", "Stage", "Processing days", "Employee reason", "HR note", "Rejection reason", "Cancellation reason"
        ), List.of(
                HrReportDetailColumnKind.DATETIME, HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.NUMBER, HrReportDetailColumnKind.TEXT,
                HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.OPTIONAL_NUMBER, HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT, HrReportDetailColumnKind.TEXT,
                HrReportDetailColumnKind.TEXT
        ), (row, reportRow) -> {
            int c = 0;
            writeDateTimeCell(row, c++, reportRow.decisionAt(), styles);
            writeTextCell(row, c++, reportRow.decisionActorLabel(), styles, false);
            writeTextCell(row, c++, HrReportExportLabels.displaySourceType(reportRow.sourceType()), styles, false);
            writeTextCell(row, c++, reportRow.requestSubtype(), styles, false);
            writeNumberCell(row, c++, reportRow.id(), styles);
            writeTextCell(row, c++, reportRow.employeeName(), styles, false);
            writeTextCell(row, c++, reportRow.status(), styles, false);
            writeTextCell(row, c++, reportRow.stage(), styles, false);
            writeOptionalNumberCell(row, c++, reportRow.processingDays(), true, styles);
            writeTextCell(row, c++, reportRow.reason(), styles, true);
            writeTextCell(row, c++, reportRow.hrNote(), styles, true);
            writeTextCell(row, c++, reportRow.rejectionReason(), styles, true);
            writeTextCell(row, c++, reportRow.cancellationReason(), styles, true);
        }, styles, Set.of(9, 10, 11, 12));
    }

    private void writeDetailedSheet(Workbook workbook, String sheetName, List<HrReportRowDto> rows,
                                    List<String> headers, List<HrReportDetailColumnKind> columnKinds, BiConsumer<Row, HrReportRowDto> rowWriter,
                                    HrReportWorkbookStyles styles, Set<Integer> wrapColumns) {
        Sheet sheet = workbook.createSheet(sheetName);
        int rowIndex = 0;
        Row headerRow = sheet.createRow(rowIndex++);
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers.get(i));
            cell.setCellStyle(styles.tableHeaderStyle());
        }

        if (rows.isEmpty()) {
            Row emptyRow = sheet.createRow(rowIndex++);
            Cell emptyCell = emptyRow.createCell(0);
            emptyCell.setCellValue("No rows in the selected scope.");
            emptyCell.setCellStyle(styles.textStyle());
        } else {
            for (HrReportRowDto row : rows) {
                Row dataRow = sheet.createRow(rowIndex++);
                rowWriter.accept(dataRow, row);
                HrReportExportSanitizer.sanitizeDetailedRow(dataRow, columnKinds);
            }
        }

        sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, rowIndex - 1), 0, headers.size() - 1));
        sheet.createFreezePane(0, 1);
        autoSize(sheet, headers.size(), wrapColumns);
    }

    private void writeSourceSheet(Workbook workbook, String sheetName, List<HrReportRowDto> rows,
                                  List<String> headers, Function<HrReportRowDto, List<?>> rowMapper,
                                  HrReportWorkbookStyles styles, Set<Integer> wrapColumns) {
        Sheet sheet = workbook.createSheet(sheetName);

        int rowIndex = 0;
        Row headerRow = sheet.createRow(rowIndex++);
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers.get(i));
            cell.setCellStyle(styles.tableHeaderStyle());
        }

        if (rows.isEmpty()) {
            Row emptyRow = sheet.createRow(rowIndex++);
            Cell emptyCell = emptyRow.createCell(0);
            emptyCell.setCellValue("No rows in the selected scope.");
            emptyCell.setCellStyle(styles.textStyle());
        } else {
            for (HrReportRowDto row : rows) {
                Row dataRow = sheet.createRow(rowIndex++);
                List<?> values = rowMapper.apply(row);
                for (int i = 0; i < values.size(); i++) {
                    Cell cell = dataRow.createCell(i);
                    writeCellValue(cell, values.get(i), styles, wrapColumns.contains(i));
                }
            }
        }

        sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, rowIndex - 1), 0, headers.size() - 1));
        sheet.createFreezePane(0, 1);
        autoSize(sheet, headers.size(), wrapColumns);
    }

    private void writeSimpleTable(Sheet sheet, int startRow, String title, List<String> headers,
                                  List<List<String>> rows, Workbook workbook) {
        int rowIndex = startRow;
        CellStyle sectionStyle = HrReportExcelStyleHelper.boldStyle(workbook, 12, false);
        CellStyle headerStyle = HrReportExcelStyleHelper.headerStyle(workbook);

        Row titleRow = sheet.createRow(rowIndex++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(title);
        titleCell.setCellStyle(sectionStyle);

        Row headerRow = sheet.createRow(rowIndex++);
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers.get(i));
            cell.setCellStyle(headerStyle);
        }

        if (rows.isEmpty()) {
            Row emptyRow = sheet.createRow(rowIndex++);
            emptyRow.createCell(0).setCellValue("No rows in the selected scope.");
        } else {
            for (List<String> rowValues : rows) {
                Row row = sheet.createRow(rowIndex++);
                for (int i = 0; i < rowValues.size(); i++) {
                    row.createCell(i).setCellValue(rowValues.get(i));
                }
            }
        }
    }

    private Cell writeTextCell(Row row, int column, Object value, HrReportWorkbookStyles styles, boolean wrap) {
        Cell cell = row.createCell(column);
        String cleaned = HrReportExportSanitizer.sanitizeTextValue(value);
        cell.setCellStyle(wrap ? styles.wrapTextStyle() : styles.textStyle());
        cell.setCellValue(cleaned == null ? "" : cleaned);
        return cell;
    }

    private Cell writeDateCell(Row row, int column, Object value, HrReportWorkbookStyles styles) {
        Cell cell = row.createCell(column);
        LocalDate date = value instanceof LocalDate localDate ? localDate : null;
        if (date == null) {
            cell.setCellStyle(styles.dateStyle());
            cell.setCellValue("");
            return cell;
        }
        cell.setCellValue(java.sql.Date.valueOf(date));
        cell.setCellStyle(styles.dateStyle());
        return cell;
    }

    private Cell writeDateTimeCell(Row row, int column, Object value, HrReportWorkbookStyles styles) {
        Cell cell = row.createCell(column);
        LocalDateTime dateTime = value instanceof LocalDateTime localDateTime ? localDateTime : null;
        if (dateTime == null) {
            cell.setCellStyle(styles.dateTimeStyle());
            cell.setCellValue("");
            return cell;
        }
        cell.setCellValue(java.sql.Timestamp.valueOf(dateTime));
        cell.setCellStyle(styles.dateTimeStyle());
        return cell;
    }

    private Cell writeTimeCell(Row row, int column, Object value, HrReportWorkbookStyles styles) {
        Cell cell = row.createCell(column);
        LocalTime time = value instanceof LocalTime localTime ? localTime : null;
        if (time == null) {
            cell.setCellStyle(styles.timeStyle());
            cell.setCellValue("");
            return cell;
        }
        cell.setCellValue(HrReportExportLabels.formatTime(time));
        cell.setCellStyle(styles.timeStyle());
        return cell;
    }

    private Cell writeNumberCell(Row row, int column, Object value, HrReportWorkbookStyles styles) {
        Cell cell = row.createCell(column);
        if (!(value instanceof Number number)) {
            cell.setCellStyle(styles.integerStyle());
            cell.setCellValue("");
            return cell;
        }
        cell.setCellValue(number.doubleValue());
        if (number instanceof Integer || number instanceof Long || number instanceof Short || number instanceof Byte) {
            cell.setCellStyle(styles.integerStyle());
        } else {
            cell.setCellStyle(styles.decimalStyle());
        }
        return cell;
    }

    private Cell writeOptionalNumberCell(Row row, int column, Object value, boolean applicable, HrReportWorkbookStyles styles) {
        Cell cell = row.createCell(column);
        if (!applicable || !(value instanceof Number number)) {
            cell.setCellStyle(styles.integerStyle());
            cell.setCellValue("");
            return cell;
        }
        cell.setCellValue(number.doubleValue());
        if (number instanceof Integer || number instanceof Long || number instanceof Short || number instanceof Byte) {
            cell.setCellStyle(styles.integerStyle());
        } else {
            cell.setCellStyle(styles.decimalStyle());
        }
        return cell;
    }

    private long count(List<HrReportRowDto> rows, Function<HrReportRowDto, Boolean> predicate) {
        return rows.stream().filter(row -> Boolean.TRUE.equals(predicate.apply(row))).count();
    }

    private String formatAverageDays(List<Long> values) {
        return String.format(Locale.ROOT, "%.1f days", HrReportExportLabels.averageLong(values));
    }

    private String resolveSourceScopeLabel(HrReportExportRequest request) {
        List<HrReportExportRequest.SourceType> sourceTypes = request.getSourceTypes();
        if (sourceTypes == null || sourceTypes.isEmpty() || sourceTypes.size() == HrReportExportRequest.SourceType.values().length) {
            return "All sources";
        }
        return sourceTypes.stream().map(HrReportExportLabels::displaySourceTypePlural).collect(Collectors.joining(", "));
    }

    private String resolveDateBasisLabel(HrReportExportRequest request) {
        HrReportExportRequest.DateBasis basis = request.getDateBasis() != null ? request.getDateBasis() : HrReportExportRequest.DateBasis.SUBMITTED;
        return switch (basis) {
            case SUBMITTED -> "Submitted date";
            case DECISION -> "Decision date";
            case CLOSED -> "Closed date";
        };
    }

    private String resolvePeriodLabel(HrReportExportRequest request) {
        if (request.getDateFrom() == null && request.getDateTo() == null) {
            return "All dates";
        }
        if (request.getDateFrom() != null && request.getDateTo() != null) {
            return HrReportExportLabels.formatDate(request.getDateFrom()) + " → " + HrReportExportLabels.formatDate(request.getDateTo());
        }
        if (request.getDateFrom() != null) {
            return "From " + HrReportExportLabels.formatDate(request.getDateFrom());
        }
        return "Until " + HrReportExportLabels.formatDate(request.getDateTo());
    }

    private String resolveStatusLabel(HrReportExportRequest request) {
        if (request.getStatusGroup() != null) {
            return formatEnum(request.getStatusGroup().name());
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            return formatEnum(request.getStatus().trim());
        }
        return "All statuses";
    }

    private String resolveDepartmentLabel(HrReportExportRequest request, List<HrReportRowDto> rows) {
        if (request.getDepartmentId() == null) {
            return "All departments";
        }
        return rows.stream()
                .filter(row -> Objects.equals(row.departmentId(), request.getDepartmentId()))
                .map(HrReportRowDto::departmentName)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse("Department #" + request.getDepartmentId());
    }

    private String resolveTeamLabel(HrReportExportRequest request, List<HrReportRowDto> rows) {
        if (request.getTeamId() == null) {
            return "All teams";
        }
        return rows.stream()
                .filter(row -> Objects.equals(row.teamId(), request.getTeamId()))
                .map(HrReportRowDto::teamName)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse("Team #" + request.getTeamId());
    }

    private void writeObjectRow(Row row, List<Object> values, HrReportWorkbookStyles styles, Set<Integer> wrapColumns) {
        for (int i = 0; i < values.size(); i++) {
            Cell cell = row.createCell(i);
            writeCellValue(cell, values.get(i), styles, wrapColumns.contains(i));
        }
    }

    private void writeCellValue(Cell cell, Object value, HrReportWorkbookStyles styles, boolean wrap) {
        if (value == null) {
            cell.setCellStyle(wrap ? styles.wrapTextStyle() : styles.textStyle());
            cell.setCellValue("");
            return;
        }
        if (value instanceof LocalDate localDate) {
            cell.setCellValue(java.sql.Date.valueOf(localDate));
            cell.setCellStyle(styles.dateStyle());
            return;
        }
        if (value instanceof LocalDateTime localDateTime) {
            cell.setCellValue(java.sql.Timestamp.valueOf(localDateTime));
            cell.setCellStyle(styles.dateTimeStyle());
            return;
        }
        if (value instanceof LocalTime localTime) {
            cell.setCellValue(HrReportExportLabels.formatTime(localTime));
            cell.setCellStyle(styles.timeStyle());
            return;
        }
        if (value instanceof Number number) {
            if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
                cell.setCellValue(number.doubleValue());
                cell.setCellStyle(styles.integerStyle());
            } else {
                cell.setCellValue(number.doubleValue());
                cell.setCellStyle(styles.decimalStyle());
            }
            return;
        }
        if (value instanceof Boolean bool) {
            cell.setCellValue(bool ? "Yes" : "No");
            cell.setCellStyle(wrap ? styles.wrapTextStyle() : styles.textStyle());
            return;
        }
        cell.setCellValue(String.valueOf(value));
        cell.setCellStyle(wrap ? styles.wrapTextStyle() : styles.textStyle());
    }

    private void setSummaryColumnWidths(Sheet sheet) {
        int[] widths = {28, 20, 22, 20, 20, 20};
        for (int i = 0; i < widths.length; i++) {
            sheet.setColumnWidth(i, widths[i] * 256);
        }
    }

    private void autoSize(Sheet sheet, int columns, Set<Integer> wrapColumns) {
        for (int i = 0; i < columns; i++) {
            sheet.autoSizeColumn(i);
            int width = sheet.getColumnWidth(i);
            int minWidth = 12 * 256;
            int maxWidth = wrapColumns.contains(i) ? 28 * 256 : 24 * 256;
            sheet.setColumnWidth(i, Math.max(minWidth, Math.min(width, maxWidth)));
        }
    }

    private boolean shouldIncludeSheet(HrReportExportRequest request, String sheetKey) {
        List<String> includeSheets = request.getIncludeSheets();
        if (includeSheets == null || includeSheets.isEmpty()) {
            return true;
        }
        Set<String> normalized = includeSheets.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(HrReportExportLabels::sheetKey)
                .collect(Collectors.toCollection(HashSet::new));
        return normalized.contains(HrReportExportLabels.sheetKey(sheetKey));
    }

    private HrReportRowDto baseRow(Long id,
                                   HrReportExportRequest.SourceType sourceType,
                                   String requestTypeLabel,
                                   String requestSubtype,
                                   String status,
                                   String stage,
                                   HrReportExportRequest.StatusGroup statusGroup,
                                   EmployeeSnapshot employee,
                                   LocalDateTime submittedAt,
                                   LocalDateTime decisionAt,
                                   LocalDateTime closedAt,
                                   Long processingDays,
                                   Long pendingAgeDays,
                                   String decisionActorId,
                                   String decisionActorLabel,
                                   String reason,
                                   String hrNote,
                                   String rejectionReason,
                                   String cancellationReason) {
        HrReportRowDto row = new HrReportRowDto();
        row.setId(id);
        row.setSourceType(sourceType);
        row.setRequestTypeLabel(requestTypeLabel);
        row.setRequestSubtype(HrReportExportSanitizer.cleanTextValue(requestSubtype));
        row.setStatus(readableStatusLabel(status));
        row.setStage(readableStageLabel(stage));
        row.setStatusGroup(statusGroup);
        row.setEmployeeId(employee.employeeId());
        row.setEmployeeName(HrReportExportSanitizer.cleanProfileValue(employee.fullName()));
        row.setUsername(HrReportExportSanitizer.cleanProfileValue(employee.username()));
        row.setEmail(HrReportExportSanitizer.cleanProfileValue(employee.email()));
        row.setHireDate(employee.hireDate());
        row.setDepartmentId(employee.departmentId());
        row.setDepartmentName(HrReportExportSanitizer.cleanProfileValue(employee.departmentName()));
        row.setTeamId(employee.teamId());
        row.setTeamName(HrReportExportSanitizer.cleanProfileValue(employee.teamName()));
        row.setJobTitle(HrReportExportSanitizer.cleanProfileValue(employee.jobTitle()));
        row.setRole(HrReportExportSanitizer.cleanProfileValue(employee.role()));
        row.setSubmittedAt(submittedAt);
        row.setDecisionAt(decisionAt);
        row.setClosedAt(closedAt);
        row.setProcessingDays(processingDays);
        row.setPendingAgeDays(pendingAgeDays);
        row.setDecisionActorId(HrReportExportSanitizer.cleanTextValue(decisionActorId));
        row.setDecisionActorLabel(HrReportExportSanitizer.cleanTextValue(decisionActorLabel));
        row.setReason(HrReportExportSanitizer.cleanWorkflowText(reason));
        row.setHrNote(HrReportExportSanitizer.cleanWorkflowText(hrNote));
        row.setRejectionReason(HrReportExportSanitizer.cleanWorkflowText(rejectionReason));
        row.setCancellationReason(HrReportExportSanitizer.cleanWorkflowText(cancellationReason));
        return row;
    }

    private record EmployeeSnapshot(Long employeeId,
                                    String fullName,
                                    String username,
                                    String email,
                                    LocalDate hireDate,
                                    Long departmentId,
                                    String departmentName,
                                    Long teamId,
                                    String teamName,
                                    String jobTitle,
                                    String role) {

        static EmployeeSnapshot empty() {
            return new EmployeeSnapshot(null, "", "", "", null, null, "", null, "", "", "");
        }

        static EmployeeSnapshot fromUser(User user) {
            if (user == null) {
                return empty();
            }
            Person person = user.getPerson();
            String fullName = "";
            String email = "";
            LocalDate hireDate = null;
            Long departmentId = null;
            String departmentName = "";
            String jobTitle = "";
            Long teamId = null;
            String teamName = "";
            if (person != null) {
                String firstName = person.getFirstName() != null ? person.getFirstName().trim() : "";
                String lastName = person.getLastName() != null ? person.getLastName().trim() : "";
                fullName = (firstName + " " + lastName).trim();
                email = person.getEmail() != null ? person.getEmail() : "";
                hireDate = person.getHireDate();
                departmentId = person.getDepartmentId();
                departmentName = person.getDepartment() != null ? person.getDepartment() : "";
                jobTitle = person.getJobTitle() != null ? person.getJobTitle() : "";
            }
            if (user.getTeam() != null) {
                teamId = user.getTeam().getId();
                teamName = user.getTeam().getName() != null ? user.getTeam().getName() : "";
            }
            return new EmployeeSnapshot(
                    user.getId(),
                    fullName,
                    user.getUsername() != null ? user.getUsername() : "",
                    email,
                    hireDate,
                    departmentId,
                    departmentName,
                    teamId,
                    teamName,
                    jobTitle,
                    user.getRole() != null ? user.getRole().name() : ""
            );
        }

        static EmployeeSnapshot fromReportUserMap(Map<String, Object> userMap) {
            if (userMap == null) {
                return empty();
            }
            Long employeeId = asLong(userMap.get("id"));
            String username = asString(userMap.get("username"));
            String role = asString(userMap.get("role"));
            Map<String, Object> personalInfo = asMap(userMap.get("personalInfo"));
            Map<String, Object> teamInfo = asMap(userMap.get("teamInfo"));

            String firstName = asString(personalInfo != null ? personalInfo.get("firstName") : null);
            String lastName = asString(personalInfo != null ? personalInfo.get("lastName") : null);
            String fullName = (firstName + " " + lastName).trim();
            if (fullName.isBlank()) {
                fullName = username != null ? username : "";
            }

            return new EmployeeSnapshot(
                    employeeId,
                    fullName,
                    username != null ? username : "",
                    asString(personalInfo != null ? personalInfo.get("email") : null),
                    parseDate(asString(personalInfo != null ? personalInfo.get("hireDate") : null)),
                    asLong(personalInfo != null ? personalInfo.get("departmentId") : null),
                    asString(personalInfo != null ? personalInfo.get("department") : null),
                    asLong(teamInfo != null ? teamInfo.get("teamId") : null),
                    asString(teamInfo != null ? teamInfo.get("teamName") : null),
                    asString(personalInfo != null ? personalInfo.get("jobTitle") : null),
                    role != null ? role : ""
            );
        }

        EmployeeSnapshot merge(EmployeeSnapshot fallback) {
            if (fallback == null) {
                return this;
            }
            return new EmployeeSnapshot(
                    employeeId != null ? employeeId : fallback.employeeId,
                    choose(fullName, fallback.fullName),
                    choose(username, fallback.username),
                    choose(email, fallback.email),
                    hireDate != null ? hireDate : fallback.hireDate,
                    departmentId != null ? departmentId : fallback.departmentId,
                    choose(departmentName, fallback.departmentName),
                    teamId != null ? teamId : fallback.teamId,
                    choose(teamName, fallback.teamName),
                    choose(jobTitle, fallback.jobTitle),
                    choose(role, fallback.role)
            );
        }

        private static String choose(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value;
        }

        @SuppressWarnings("unchecked")
        private static Map<String, Object> asMap(Object value) {
            if (value instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
            return null;
        }

        private static String asString(Object value) {
            return value == null ? null : String.valueOf(value);
        }

        private static Long asLong(Object value) {
            if (value == null) {
                return null;
            }
            if (value instanceof Number number) {
                return number.longValue();
            }
            try {
                return Long.parseLong(String.valueOf(value));
            } catch (NumberFormatException ex) {
                return null;
            }
        }

        private static LocalDate parseDate(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            try {
                return LocalDate.parse(value);
            } catch (Exception ex) {
                return null;
            }
        }
    }
}
