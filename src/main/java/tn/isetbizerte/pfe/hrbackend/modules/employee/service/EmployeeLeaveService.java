package tn.isetbizerte.pfe.hrbackend.modules.employee.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.isetbizerte.pfe.hrbackend.common.enums.ApprovalDecision;
import tn.isetbizerte.pfe.hrbackend.common.enums.LeaveStatus;
import tn.isetbizerte.pfe.hrbackend.common.enums.LeaveType;
import tn.isetbizerte.pfe.hrbackend.common.exception.BadRequestException;
import tn.isetbizerte.pfe.hrbackend.common.exception.ResourceNotFoundException;
import tn.isetbizerte.pfe.hrbackend.modules.employee.dto.CreateLeaveRequestDto;
import tn.isetbizerte.pfe.hrbackend.modules.employee.dto.LeaveRequestResponseDto;
import tn.isetbizerte.pfe.hrbackend.modules.employee.entity.LeaveRequest;
import tn.isetbizerte.pfe.hrbackend.modules.employee.repository.LeaveRequestRepository;
import tn.isetbizerte.pfe.hrbackend.common.enums.TypeRole;
import tn.isetbizerte.pfe.hrbackend.common.exception.UnauthorizedException;
import tn.isetbizerte.pfe.hrbackend.common.event.RequestEvent;
import tn.isetbizerte.pfe.hrbackend.infrastructure.kafka.producer.RequestEventProducer;
import tn.isetbizerte.pfe.hrbackend.modules.calendar.service.WorkingDayService;
import tn.isetbizerte.pfe.hrbackend.modules.history.service.RequestHistoryService;
import tn.isetbizerte.pfe.hrbackend.modules.team.entity.Team;
import tn.isetbizerte.pfe.hrbackend.modules.team.repository.TeamRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.Person;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.UserRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.PersonRepository;
import tn.isetbizerte.pfe.hrbackend.infrastructure.email.HREmailService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class EmployeeLeaveService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final UserRepository          userRepository;
    private final TeamRepository          teamRepository;
    private final PersonRepository        personRepository;
    private final LeaveScoreEngine        leaveScoreEngine;
    private final HREmailService          emailService;
    private final RequestEventProducer    requestEventProducer;
    private final RequestHistoryService   historyService;
    private final LeaveBalanceService      leaveBalanceService;
    private final WorkingDayService        workingDayService;
    private final LeaveValidationService   leaveValidationService;

    public EmployeeLeaveService(
            LeaveRequestRepository leaveRequestRepository,
            UserRepository userRepository,
            TeamRepository teamRepository,
            PersonRepository personRepository,
            LeaveScoreEngine leaveScoreEngine,
            HREmailService emailService,
            RequestEventProducer requestEventProducer,
            RequestHistoryService historyService,
            LeaveBalanceService leaveBalanceService,
            WorkingDayService workingDayService,
            LeaveValidationService leaveValidationService
    ) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.userRepository         = userRepository;
        this.teamRepository         = teamRepository;
        this.personRepository       = personRepository;
        this.leaveScoreEngine       = leaveScoreEngine;
        this.emailService           = emailService;
        this.requestEventProducer   = requestEventProducer;
        this.historyService         = historyService;
        this.leaveBalanceService    = leaveBalanceService;
        this.workingDayService      = workingDayService;
        this.leaveValidationService = leaveValidationService;
    }

    /**
     * Create a new leave request.
     * Business rule:
     * - EMPLOYEE: goes through full 2-step flow (TL to HR)
     * - TEAM_LEADER: TL step is auto-approved, goes directly to HR
     */
    @CacheEvict(value = "calendarLeaves", allEntries = true)
    @Transactional
    public LeaveRequestResponseDto createLeaveRequest(String userIdentifier, CreateLeaveRequestDto dto) {
        log.info("Creating leave request for user: {}", userIdentifier);

        User user = resolveUser(userIdentifier);

        validateTeamSetupForLeaveSubmission(user);

        int requestedWorkingDays = validateLeaveRequestDates(dto.getStartDate(), dto.getEndDate());
        validateNoOverlappingLeave(user, dto.getStartDate(), dto.getEndDate());
        validateSickLeaveCreationRules(dto);
        validateAnnualLeaveCreationRules(user, dto, requestedWorkingDays);
        leaveBalanceService.reserveForRequest(user, dto.getLeaveType(), dto.getStartDate(), requestedWorkingDays);

        LeaveRequest leaveRequest = new LeaveRequest();
        leaveRequest.setUser(user);
        leaveRequest.setLeaveType(dto.getLeaveType());
        leaveRequest.setStartDate(dto.getStartDate());
        leaveRequest.setEndDate(dto.getEndDate());
        leaveRequest.setNumberOfDays(requestedWorkingDays);
        leaveRequest.setReason(dto.getReason());
        leaveRequest.setRequestDate(LocalDateTime.now());

        // Team Leader's leave goes directly to HR - TL step auto-approved
        if (user.getRole() == TypeRole.TEAM_LEADER) {
            leaveRequest.setTeamLeaderDecision(ApprovalDecision.APPROVED);
            log.info("Team Leader leave request — TL step auto-approved, routing directly to HR");
        }

        // Run scoring engine immediately on creation
        leaveScoreEngine.evaluate(leaveRequest);
        log.info("Leave scored: score={}, recommendation={}",
                leaveRequest.getSystemScore(), leaveRequest.getSystemRecommendation());

        LeaveRequest saved = leaveRequestRepository.save(leaveRequest);
        historyService.record(
                "LEAVE",
                "CREATED",
                saved.getId(),
                user.getKeycloakId(),
                saved.getReason()
        );
        log.info("Leave request created with ID: {} for user: {}", saved.getId(), user.getUsername());

        // Notify requester (EMPLOYEE or TEAM_LEADER) that the request was submitted.
        try {
            requestEventProducer.publish(
                    new tn.isetbizerte.pfe.hrbackend.common.event.RequestEvent(
                            "LEAVE_SUBMITTED",
                            "LEAVE",
                            saved.getId(),
                            user.getKeycloakId(),
                            user.getKeycloakId(),
                            null
                    )
            );
        } catch (Exception e) {
            log.warn("Failed to enqueue LEAVE_SUBMITTED request event for leaveId={}", saved.getId(), e);
        }

        return mapToResponseDto(saved);
    }

    @CacheEvict(value = "calendarLeaves", allEntries = true)
    @Transactional
    public LeaveRequestResponseDto updateMyLeaveRequest(Long leaveId, String requesterKeycloakId, CreateLeaveRequestDto dto) {
        LeaveRequest leave = leaveRequestRepository.findById(leaveId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found with ID: " + leaveId));

        if (leave.getUser() == null || !requesterKeycloakId.equals(leave.getUser().getKeycloakId())) {
            throw new AccessDeniedException("Only the owner can edit this leave request.");
        }

        String fromStage = computeApprovalStage(leave);
        if (!"PENDING_TL".equals(fromStage)) {
            throw new BadRequestException("Only leave requests still pending Team Leader review can be edited by the employee.");
        }

        User user = leave.getUser();
        leaveBalanceService.releaseReserved(leave);

        int requestedWorkingDays = validateLeaveRequestDates(dto.getStartDate(), dto.getEndDate());
        validateNoOverlappingLeave(user, dto.getStartDate(), dto.getEndDate(), leave.getId());
        validateSickLeaveCreationRules(dto);
        validateAnnualLeaveCreationRules(user, dto, requestedWorkingDays);
        leaveBalanceService.reserveForRequest(user, dto.getLeaveType(), dto.getStartDate(), requestedWorkingDays);

        leave.setLeaveType(dto.getLeaveType());
        leave.setStartDate(dto.getStartDate());
        leave.setEndDate(dto.getEndDate());
        leave.setNumberOfDays(requestedWorkingDays);
        leave.setReason(dto.getReason());
        leave.setUpdatedAt(LocalDateTime.now());

        leaveScoreEngine.evaluate(leave);

        historyService.record(
                "LEAVE",
                "EMPLOYEE_EDITED",
                leave.getId(),
                requesterKeycloakId,
                "Employee edited the request.",
                fromStage,
                computeApprovalStage(leave)
        );

        LeaveRequest saved = leaveRequestRepository.save(leave);
        return mapToResponseDto(saved);
    }

    /**
     * Get all leave requests for an employee
     */
    public Page<LeaveRequestResponseDto> getMyLeaveRequests(String userIdentifier, Pageable pageable) {
        log.info("Fetching leave requests for user: {}", userIdentifier);

        User user = resolveUser(userIdentifier);

        return leaveRequestRepository.findByUser(user, pageable)
                .map(this::mapToResponseDto);
    }

    private User resolveUser(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new ResourceNotFoundException("User not found");
        }
        return userRepository.findByKeycloakId(identifier)
                .or(() -> userRepository.findByUsername(identifier))
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + identifier));
    }

    // -----------------------------------------------------------------------
    // Private validators — delegate to LeaveValidationService (single source of truth)
    // -----------------------------------------------------------------------

    private void validateTeamSetupForLeaveSubmission(User user) {
        leaveValidationService.throwingValidateTeamSetup(user);
    }

    private int validateLeaveRequestDates(LocalDate startDate, LocalDate endDate) {
        return leaveValidationService.throwingValidateDates(startDate, endDate);
    }

    private void validateNoOverlappingLeave(User user, LocalDate startDate, LocalDate endDate) {
        leaveValidationService.throwingValidateNoOverlap(user, startDate, endDate);
    }

    private void validateNoOverlappingLeave(User user, LocalDate startDate, LocalDate endDate, Long excludedLeaveId) {
        leaveValidationService.throwingValidateNoOverlap(user, startDate, endDate, excludedLeaveId);
    }

    private void validateSickLeaveCreationRules(CreateLeaveRequestDto dto) {
        if (dto.getLeaveType() != LeaveType.SICK) return;
        leaveValidationService.throwingValidateSickLeave(dto.getStartDate());
    }

    private void validateAnnualLeaveCreationRules(User user, CreateLeaveRequestDto dto, int requestedWorkingDays) {
        if (dto.getLeaveType() != LeaveType.ANNUAL) return;
        leaveValidationService.throwingValidateAnnualLeave(user, dto.getStartDate(), requestedWorkingDays);
    }

    // -----------------------------------------------------------------------
    // Decision flow
    // -----------------------------------------------------------------------

    @CacheEvict(value = "calendarLeaves", allEntries = true)
    @Transactional
    public LeaveRequestResponseDto cancelMyLeaveRequest(Long leaveId, String requesterKeycloakId) {
        LeaveRequest leave = leaveRequestRepository.findById(leaveId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found with ID: " + leaveId));

        if (leave.getUser() == null || !requesterKeycloakId.equals(leave.getUser().getKeycloakId())) {
            throw new AccessDeniedException("Only the owner can cancel this leave request.");
        }
        if (leave.getStatus() == LeaveStatus.CANCELLED_BY_EMPLOYEE) {
            return mapToResponseDto(leave);
        }
        String fromStage = computeApprovalStage(leave);
        if (!isEmployeeCancellationAllowed(fromStage)) {
            throw new BadRequestException("Only leave requests still pending approval can be canceled by the employee.");
        }
        LocalDateTime now = LocalDateTime.now();
        leaveBalanceService.releaseReserved(leave);
        leave.setStatus(LeaveStatus.CANCELLED_BY_EMPLOYEE);
        leave.setCanceledBy(requesterKeycloakId);
        leave.setCanceledAt(now);
        leave.setUpdatedAt(now);

        historyService.record(
                "LEAVE",
                "EMPLOYEE_CANCELLED",
                leave.getId(),
                requesterKeycloakId,
                "Employee cancelled the request.",
                fromStage,
                computeApprovalStage(leave)
        );
        try {
            requestEventProducer.publish(
                    new RequestEvent("LEAVE_CANCELLED_BY_EMPLOYEE", "LEAVE", leave.getId(),
                            leave.getUser().getKeycloakId(), requesterKeycloakId, null)
            );
        } catch (Exception e) {
            log.warn("Failed to enqueue LEAVE_CANCELLED_BY_EMPLOYEE request event for leaveId={}", leave.getId(), e);
        }
        return mapToResponseDto(leaveRequestRepository.save(leave));
    }

    /**
     * Get a specific leave request by ID
     */
    @Transactional(readOnly = true)
    public LeaveRequestResponseDto getLeaveRequestById(Long leaveId, String requesterKeycloakId) {
        LeaveRequest leaveRequest = leaveRequestRepository.findById(leaveId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found with ID: " + leaveId));

        User requester = userRepository.findByKeycloakId(requesterKeycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("Requester user not found"));

        boolean isOwner = leaveRequest.getUser() != null
                && requesterKeycloakId.equals(leaveRequest.getUser().getKeycloakId());
        boolean isHR = requester.getRole() == TypeRole.HR_MANAGER;
        boolean isTeamLeaderOfEmployee = requester.getRole() == TypeRole.TEAM_LEADER
                && leaveRequest.getUser() != null
                && leaveRequest.getUser().getTeam() != null
                && leaveRequest.getUser().getTeam().getTeamLeader() != null
                && requesterKeycloakId.equals(leaveRequest.getUser().getTeam().getTeamLeader().getKeycloakId());

        if (!isOwner && !isTeamLeaderOfEmployee && !isHR) {
            throw new AccessDeniedException("You are not allowed to view this leave request.");
        }

        return mapToResponseDto(leaveRequest);
    }

    /**
     * Get pending leave requests for a Team Leader - only their team's requests.
     */
    public Page<LeaveRequestResponseDto> getPendingLeaveRequestsForTeamLeader(String keycloakId, Pageable pageable) {
        log.info("Fetching pending leave requests for team leader: {}", keycloakId);

        Team team = teamRepository.findByTeamLeaderKeycloakId(keycloakId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No team assigned to this Team Leader. Ask HR to create and assign you a team."));

        return leaveRequestRepository.findPendingByTeamIdExcludingLeader(team.getId(), keycloakId, pageable)
                .map(this::mapToResponseDto);
    }

    /**
     * Get all leave requests for a Team Leader's team (any status).
     */
    public Page<LeaveRequestResponseDto> getAllLeaveRequestsForTeamLeader(String keycloakId, Pageable pageable) {
        Team team = teamRepository.findByTeamLeaderKeycloakId(keycloakId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No team assigned to this Team Leader."));

        return leaveRequestRepository.findAllByTeamIdExcludingLeader(team.getId(), keycloakId, pageable)
                .map(this::mapToResponseDto);
    }

    /**
     * Team Leader approves or rejects a leave request.
     * Security: Team Leader can ONLY act on requests from employees in their team.
     */
    @CacheEvict(value = "calendarLeaves", allEntries = true)
    @Transactional
    public LeaveRequestResponseDto teamLeaderDecision(Long leaveId, boolean approve, String leaderKeycloakId) {
        return teamLeaderDecision(leaveId, approve, leaderKeycloakId, null);
    }

    @CacheEvict(value = "calendarLeaves", allEntries = true)
    @Transactional
    public LeaveRequestResponseDto teamLeaderDecision(Long leaveId, boolean approve, String leaderKeycloakId, String decisionReason) {
        log.info("Team Leader {} leave request ID: {}", approve ? "approving" : "rejecting", leaveId);

        // Fetch the leave request
        LeaveRequest leave = leaveRequestRepository.findById(leaveId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found with ID: " + leaveId));

        // TL cannot approve/reject own leave or any TL-submitted leave
        if (leave.getUser() != null) {
            if (leaderKeycloakId != null && leaderKeycloakId.equals(leave.getUser().getKeycloakId())) {
                throw new AccessDeniedException("You cannot approve or reject your own leave request.");
            }
            if (leave.getUser().getRole() == TypeRole.TEAM_LEADER) {
                throw new AccessDeniedException("Team Leader leave requests bypass team approval and go directly to HR.");
            }
        }

        normalizeWorkflowState(leave);

        // Idempotency: if already processed, return current state
        if (leave.getStatus() != LeaveStatus.PENDING || leave.getTeamLeaderDecision() != ApprovalDecision.PENDING) {
            return mapToResponseDto(leave);
        }

        String fromStage = computeApprovalStage(leave);

        // Validate Team Leader has a team
        Team leaderTeam = teamRepository.findByTeamLeaderKeycloakId(leaderKeycloakId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No team assigned to this Team Leader. Ask HR to assign you a team."));

        // Validate the employee belongs to this Team Leader's team
        User employee = leave.getUser();
        if (employee.getTeam() == null || !employee.getTeam().getId().equals(leaderTeam.getId())) {
            throw new UnauthorizedException(
                    "You can only approve leave requests from employees in your own team.");
        }
        String tlAction = approve ? "TL_APPROVED" : "TL_REJECTED";
        if (historyService.exists("LEAVE", tlAction, leave.getId(), leaderKeycloakId)) {
            throw new BadRequestException("This decision was already processed.");
        }

        leave.setTeamLeaderDecision(approve ? ApprovalDecision.APPROVED : ApprovalDecision.REJECTED);
        LocalDateTime decisionAt = LocalDateTime.now();
        leave.setUpdatedAt(decisionAt);

        if (!approve) {
            if (decisionReason == null || decisionReason.isBlank()) {
                throw new BadRequestException("Rejection reason is required.");
            }
            leave.setDecisionReason(decisionReason.trim());
            leave.setRejectedBy(leaderKeycloakId);
            leave.setRejectedAt(decisionAt);
            leave.setStatus(LeaveStatus.REJECTED);
            leaveBalanceService.releaseReserved(leave);
            historyService.record(
                    "LEAVE",
                    "TL_REJECTED",
                    leave.getId(),
                    leaderKeycloakId,
                    decisionReason.trim(),
                    fromStage,
                    computeApprovalStage(leave)
            );
            requestEventProducer.publish(
                    new RequestEvent("LEAVE_REJECTED", "LEAVE", leave.getId(), leave.getUser().getKeycloakId(), leaderKeycloakId, decisionReason.trim())
            );
            // Email: TL rejected
            sendLeaveDecisionEmail(leave, false);
        } else {
            leave.setApprovedBy(leaderKeycloakId);
            requestEventProducer.publish(
                    new RequestEvent("LEAVE_APPROVED", "LEAVE", leave.getId(), leave.getUser().getKeycloakId(), leaderKeycloakId, null)
            );
            recalculateStatus(leave);
            historyService.record(
                    "LEAVE",
                    "TL_APPROVED",
                    leave.getId(),
                    leaderKeycloakId,
                    null,
                    fromStage,
                    computeApprovalStage(leave)
            );
        }

        return mapToResponseDto(leaveRequestRepository.save(leave));
    }

    /**
     * HR Manager approves or rejects a leave request.
     * Sets hrDecision and recalculates overall status.
     */
    @CacheEvict(value = "calendarLeaves", allEntries = true)
    @Transactional
    public LeaveRequestResponseDto hrDecision(Long leaveId, boolean approve) {
        return hrDecision(leaveId, approve, null);
    }

    @CacheEvict(value = "calendarLeaves", allEntries = true)
    @Transactional
    public LeaveRequestResponseDto hrDecision(Long leaveId, boolean approve, String decisionReason) {
        return hrDecision(leaveId, approve, decisionReason, null);
    }

    @CacheEvict(value = "calendarLeaves", allEntries = true)
    @Transactional
    public LeaveRequestResponseDto hrDecision(Long leaveId, boolean approve, String decisionReason, String hrKeycloakId) {
        log.info("HR Manager {} leave request ID: {}", approve ? "approving" : "rejecting", leaveId);

        LeaveRequest leave = leaveRequestRepository.findById(leaveId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found with ID: " + leaveId));

        // HR cannot approve/reject own leave
        if (leave.getUser() != null && hrKeycloakId != null
                && hrKeycloakId.equals(leave.getUser().getKeycloakId())) {
            throw new AccessDeniedException("You cannot approve or reject your own leave request.");
        }

        normalizeWorkflowState(leave);

        // Idempotency: if already processed, return current state
        if (leave.getStatus() != LeaveStatus.PENDING || leave.getHrDecision() != ApprovalDecision.PENDING) {
            return mapToResponseDto(leave);
        }

        String fromStage = computeApprovalStage(leave);

        if (leave.getStatus() == LeaveStatus.REJECTED) {
            throw new BadRequestException("Leave request has already been rejected");
        }
        if (leave.getStatus() == LeaveStatus.APPROVED) {
            throw new BadRequestException("Leave request has already been fully approved");
        }
        if (leave.getHrDecision() != ApprovalDecision.PENDING) {
            throw new BadRequestException("HR decision already recorded.");
        }
        // HR can only act after Team Leader has approved
        if (leave.getTeamLeaderDecision() != ApprovalDecision.APPROVED) {
            throw new BadRequestException("Team Leader must approve this request before HR can act on it.");
        }
        String hrAction = approve ? "HR_APPROVED" : "HR_REJECTED";
        if (historyService.exists("LEAVE", hrAction, leave.getId(), hrKeycloakId)) {
            throw new BadRequestException("This decision was already processed.");
        }

        leave.setHrDecision(approve ? ApprovalDecision.APPROVED : ApprovalDecision.REJECTED);
        LocalDateTime decisionAt = LocalDateTime.now();
        leave.setUpdatedAt(decisionAt);

        if (!approve) {
            if (decisionReason == null || decisionReason.isBlank()) {
                throw new BadRequestException("Rejection reason is required.");
            }
            leave.setDecisionReason(decisionReason.trim());
            leave.setRejectedBy(hrKeycloakId);
            leave.setRejectedAt(decisionAt);
            leave.setStatus(LeaveStatus.REJECTED);
            leaveBalanceService.releaseReserved(leave);
            historyService.record(
                    "LEAVE",
                    "HR_REJECTED",
                    leave.getId(),
                    hrKeycloakId,
                    decisionReason.trim(),
                    fromStage,
                    computeApprovalStage(leave)
            );
            requestEventProducer.publish(
                    new RequestEvent("LEAVE_REJECTED", "LEAVE", leave.getId(), leave.getUser().getKeycloakId(), hrKeycloakId, decisionReason.trim())
            );
            sendLeaveDecisionEmail(leave, false);
        } else {
            leave.setApprovedBy(hrKeycloakId);
            requestEventProducer.publish(
                    new RequestEvent("LEAVE_APPROVED", "LEAVE", leave.getId(), leave.getUser().getKeycloakId(), hrKeycloakId, null)
            );
            recalculateStatus(leave);
            historyService.record(
                    "LEAVE",
                    "HR_APPROVED",
                    leave.getId(),
                    hrKeycloakId,
                    null,
                    fromStage,
                    computeApprovalStage(leave)
            );
        }

        return mapToResponseDto(leaveRequestRepository.save(leave));
    }

    /**
     * Get all leave requests - HR overview.
     */
    public Page<LeaveRequestResponseDto> getAllLeaveRequests(Pageable pageable) {
        return leaveRequestRepository.findAllForHrOverview(pageable)
                .map(this::mapToResponseDto);
    }

    /**
     * Recalculate final status based on both decisions.
     * APPROVED only when BOTH team leader and HR have approved.
     */
    private void recalculateStatus(LeaveRequest leave) {
        boolean tlApproved = leave.getTeamLeaderDecision() == ApprovalDecision.APPROVED;
        boolean hrApproved = leave.getHrDecision() == ApprovalDecision.APPROVED;

        if (tlApproved && hrApproved) {
            leaveBalanceService.consumeReserved(leave);
            leave.setStatus(LeaveStatus.APPROVED);
            leave.setApprovalDate(LocalDate.now());
            leave.setApprovedAt(LocalDateTime.now());
            // Generate unique QR verification token on full approval
            if (leave.getVerificationToken() == null) {
                leave.setVerificationToken(UUID.randomUUID().toString());
            }
            if (leave.getUser() != null && leave.getUser().getPerson() != null) {
                log.info("Leave request ID {} fully APPROVED — {} day(s) added to employee record",
                        leave.getId(), leave.getNumberOfDays());
            }
            log.info("Leave request ID {} fully APPROVED — verification token generated", leave.getId());
            // Email: fully approved by HR
            sendLeaveDecisionEmail(leave, true);
        }
        // else stays PENDING until the other approver acts
    }

    private void sendLeaveDecisionEmail(LeaveRequest leave, boolean approved) {
        try {
            User user = leave.getUser();
            if (user == null || user.getPerson() == null) return;
            String email     = user.getPerson().getEmail();
            String firstName = user.getPerson().getFirstName();
            String lastName  = user.getPerson().getLastName();
            if (email == null || email.isBlank()) return;

            String refId     = "LV-" + String.format("%06d", leave.getId());
            String leaveType = formatEnum(leave.getLeaveType().name());

            if (approved) {
                emailService.sendLeaveApproved(email, firstName, lastName,
                        leaveType, leave.getStartDate(), leave.getEndDate(),
                        leave.getNumberOfDays(), refId);
            } else {
                String reason = leave.getDecisionReason() != null
                        ? leave.getDecisionReason()
                        : "The request does not meet current company policy requirements.";
                emailService.sendLeaveRejected(email, firstName, lastName,
                        leaveType, leave.getStartDate(), leave.getEndDate(), reason, refId);
            }
        } catch (Exception e) {
            log.warn("Could not send leave decision email: {}", e.getMessage());
        }
    }

    private String formatEnum(String enumName) {
        if (enumName == null) return "";
        String[] words = enumName.split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    /**
     * Convert entity to response DTO
     */
    private LeaveRequestResponseDto mapToResponseDto(LeaveRequest leaveRequest) {
        normalizeWorkflowState(leaveRequest);

        LeaveRequestResponseDto dto = new LeaveRequestResponseDto();
        dto.setId(leaveRequest.getId());
        dto.setEmployeeFullName(leaveRequest.getEmployeeFullName());
        dto.setEmployeeEmail(leaveRequest.getEmployeeEmail());
        if (leaveRequest.getUser() != null) {
            dto.setEmployeeUsername(leaveRequest.getUser().getUsername());
        }
        dto.setLeaveType(leaveRequest.getLeaveType());
        dto.setStartDate(leaveRequest.getStartDate());
        dto.setEndDate(leaveRequest.getEndDate());
        dto.setNumberOfDays(leaveRequest.getNumberOfDays());
        dto.setReason(leaveRequest.getReason());
        dto.setRequestDate(leaveRequest.getRequestDate());
        dto.setTeamLeaderDecision(leaveRequest.getTeamLeaderDecision());
        dto.setHrDecision(leaveRequest.getHrDecision());
        dto.setStatus(leaveRequest.getStatus());
        dto.setApprovalStage(computeApprovalStage(leaveRequest));
        dto.setApprovalDate(leaveRequest.getApprovalDate());
        dto.setApprovedAt(leaveRequest.getApprovedAt());
        dto.setCreatedAt(leaveRequest.getCreatedAt());
        // Scoring fields
        dto.setSystemScore(leaveRequest.getSystemScore());
        dto.setSystemRecommendation(leaveRequest.getSystemRecommendation());
        dto.setDecisionReason(leaveRequest.getDecisionReason());
        dto.setApprovedBy(leaveRequest.getApprovedBy());
        dto.setRejectedBy(leaveRequest.getRejectedBy());
        ActorDisplay rejectedBy = resolveActorDisplay(leaveRequest.getRejectedBy());
        dto.setRejectedByName(rejectedBy.name());
        dto.setRejectedByRole(rejectedBy.role());
        dto.setRejectedAt(leaveRequest.getRejectedAt());
        dto.setCanceledBy(leaveRequest.getCanceledBy());
        dto.setCanceledAt(leaveRequest.getCanceledAt());
        return dto;
    }

    private String computeApprovalStage(LeaveRequest leave) {
        normalizeWorkflowState(leave);
        if (leave.getStatus() == LeaveStatus.CANCELLED_BY_EMPLOYEE) return "CANCELLED_BY_EMPLOYEE";
        if (leave.getStatus() == LeaveStatus.REJECTED) return "REJECTED";
        if (leave.getStatus() == LeaveStatus.APPROVED) return "APPROVED";
        if (leave.getTeamLeaderDecision() == ApprovalDecision.APPROVED
                && leave.getHrDecision() == ApprovalDecision.PENDING) {
            return "PENDING_HR";
        }
        if (leave.getTeamLeaderDecision() == ApprovalDecision.PENDING) {
            return "PENDING_TL";
        }
        return "PENDING";
    }

    private boolean isEmployeeCancellationAllowed(String approvalStage) {
        return "PENDING_TL".equals(approvalStage);
    }

    private ActorDisplay resolveActorDisplay(String actorId) {
        if (actorId == null || actorId.isBlank()) {
            return new ActorDisplay(null, null);
        }

        var actor = userRepository.findByKeycloakId(actorId);
        if (actor == null || actor.isEmpty()) {
            return new ActorDisplay(null, resolveRoleValue(actorId));
        }

        User user = actor.get();
        return new ActorDisplay(resolveFullName(user), resolveRoleValue(user.getRole()));
    }

    private String resolveFullName(User user) {
        if (user == null) return null;
        Person person = user.getPerson();
        if (person != null) {
            String fullName = String.join(" ",
                    nullToBlank(person.getFirstName()),
                    nullToBlank(person.getLastName())
            ).trim();
            if (!fullName.isBlank()) return fullName;
        }
        return user.getUsername();
    }

    private String resolveRoleValue(TypeRole role) {
        return role != null ? role.name() : null;
    }

    private String resolveRoleValue(String value) {
        if (value == null) return null;
        try {
            return TypeRole.valueOf(value).name();
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private void normalizeWorkflowState(LeaveRequest leave) {
        if (leave == null) return;
        if (leave.getStatus() == LeaveStatus.CANCELLED_BY_EMPLOYEE) return;

        if (leave.getTeamLeaderDecision() == ApprovalDecision.REJECTED
                || leave.getHrDecision() == ApprovalDecision.REJECTED) {
            leave.setStatus(LeaveStatus.REJECTED);
            return;
        }

        if (leave.getTeamLeaderDecision() == ApprovalDecision.APPROVED
                && leave.getHrDecision() == ApprovalDecision.APPROVED) {
            leave.setStatus(LeaveStatus.APPROVED);
            return;
        }

        leave.setStatus(LeaveStatus.PENDING);
    }

    private record ActorDisplay(String name, String role) {}
}
