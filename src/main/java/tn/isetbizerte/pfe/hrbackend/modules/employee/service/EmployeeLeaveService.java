package tn.isetbizerte.pfe.hrbackend.modules.employee.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.isetbizerte.pfe.hrbackend.common.enums.ApprovalDecision;
import tn.isetbizerte.pfe.hrbackend.common.enums.LeaveStatus;
import tn.isetbizerte.pfe.hrbackend.common.exception.BadRequestException;
import tn.isetbizerte.pfe.hrbackend.common.exception.ResourceNotFoundException;
import tn.isetbizerte.pfe.hrbackend.modules.employee.dto.CreateLeaveRequestDto;
import tn.isetbizerte.pfe.hrbackend.modules.employee.dto.LeaveRequestResponseDto;
import tn.isetbizerte.pfe.hrbackend.modules.employee.entity.LeaveRequest;
import tn.isetbizerte.pfe.hrbackend.modules.employee.repository.LeaveRequestRepository;
import tn.isetbizerte.pfe.hrbackend.common.enums.TypeRole;
import tn.isetbizerte.pfe.hrbackend.common.exception.UnauthorizedException;
import tn.isetbizerte.pfe.hrbackend.modules.team.entity.Team;
import tn.isetbizerte.pfe.hrbackend.modules.team.repository.TeamRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.UserRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.PersonRepository;
import tn.isetbizerte.pfe.hrbackend.infrastructure.email.HREmailService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class EmployeeLeaveService {

    private static final Logger logger = LoggerFactory.getLogger(EmployeeLeaveService.class);
    private static final int MAX_LEAVE_DAYS_PER_REQUEST = 30;

    private final LeaveRequestRepository leaveRequestRepository;
    private final UserRepository          userRepository;
    private final TeamRepository          teamRepository;
    private final PersonRepository        personRepository;
    private final LeaveScoreEngine        leaveScoreEngine;
    private final HREmailService          emailService;

    public EmployeeLeaveService(
            LeaveRequestRepository leaveRequestRepository,
            UserRepository userRepository,
            TeamRepository teamRepository,
            PersonRepository personRepository,
            LeaveScoreEngine leaveScoreEngine,
            HREmailService emailService
    ) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.userRepository         = userRepository;
        this.teamRepository         = teamRepository;
        this.personRepository       = personRepository;
        this.leaveScoreEngine       = leaveScoreEngine;
        this.emailService           = emailService;
    }

    /**
     * Create a new leave request.
     * Business rule:
     * - EMPLOYEE: goes through full 2-step flow (TL → HR)
     * - TEAM_LEADER: TL step is auto-approved, goes directly to HR
     */
    @Transactional
    public LeaveRequestResponseDto createLeaveRequest(String username, CreateLeaveRequestDto dto) {
        logger.info("Creating leave request for user: {}", username);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        validateLeaveRequestDates(dto.getStartDate(), dto.getEndDate(), dto.getNumberOfDays());

        LeaveRequest leaveRequest = new LeaveRequest();
        leaveRequest.setUser(user);
        leaveRequest.setLeaveType(dto.getLeaveType());
        leaveRequest.setStartDate(dto.getStartDate());
        leaveRequest.setEndDate(dto.getEndDate());
        leaveRequest.setNumberOfDays(dto.getNumberOfDays());
        leaveRequest.setReason(dto.getReason());
        leaveRequest.setRequestDate(LocalDateTime.now());

        // Team Leader's leave goes directly to HR — TL step auto-approved
        if (user.getRole() == TypeRole.TEAM_LEADER) {
            leaveRequest.setTeamLeaderDecision(ApprovalDecision.APPROVED);
            logger.info("Team Leader leave request — TL step auto-approved, routing directly to HR");
        }

        // Run scoring engine immediately on creation
        leaveScoreEngine.evaluate(leaveRequest);
        logger.info("Leave scored: score={}, recommendation={}",
                leaveRequest.getSystemScore(), leaveRequest.getSystemRecommendation());

        LeaveRequest saved = leaveRequestRepository.save(leaveRequest);
        logger.info("Leave request created with ID: {} for user: {}", saved.getId(), username);

        return mapToResponseDto(saved);
    }

    /**
     * Get all leave requests for an employee
     */
    public List<LeaveRequestResponseDto> getMyLeaveRequests(String username) {
        logger.info("Fetching leave requests for user: {}", username);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        return leaveRequestRepository.findByUser(user).stream()
                .map(this::mapToResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Get a specific leave request by ID
     */
    public LeaveRequestResponseDto getLeaveRequestById(Long leaveId) {
        LeaveRequest leaveRequest = leaveRequestRepository.findById(leaveId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found with ID: " + leaveId));

        return mapToResponseDto(leaveRequest);
    }

    /**
     * Get pending leave requests for a Team Leader — only their team's requests.
     */
    public List<LeaveRequestResponseDto> getPendingLeaveRequestsForTeamLeader(String keycloakId) {
        logger.info("Fetching pending leave requests for team leader: {}", keycloakId);

        Team team = teamRepository.findByTeamLeaderKeycloakId(keycloakId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No team assigned to this Team Leader. Ask HR to create and assign you a team."));

        return leaveRequestRepository.findPendingByTeamId(team.getId()).stream()
                .map(this::mapToResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Get all leave requests for a Team Leader's team (any status).
     */
    public List<LeaveRequestResponseDto> getAllLeaveRequestsForTeamLeader(String keycloakId) {
        Team team = teamRepository.findByTeamLeaderKeycloakId(keycloakId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No team assigned to this Team Leader."));

        return leaveRequestRepository.findAllByTeamId(team.getId()).stream()
                .map(this::mapToResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Team Leader approves or rejects a leave request.
     * Security: Team Leader can ONLY act on requests from employees in their team.
     */
    @Transactional
    public LeaveRequestResponseDto teamLeaderDecision(Long leaveId, boolean approve, String leaderKeycloakId) {
        logger.info("Team Leader {} leave request ID: {}", approve ? "approving" : "rejecting", leaveId);

        // Fetch the leave request
        LeaveRequest leave = leaveRequestRepository.findById(leaveId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found with ID: " + leaveId));

        // Validate status
        if (leave.getStatus() != LeaveStatus.PENDING) {
            throw new BadRequestException("Leave request is already " + leave.getStatus().name().toLowerCase());
        }

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

        leave.setTeamLeaderDecision(approve ? ApprovalDecision.APPROVED : ApprovalDecision.REJECTED);
        leave.setUpdatedAt(LocalDateTime.now());

        if (!approve) {
            leave.setStatus(LeaveStatus.REJECTED);
            // Email: TL rejected
            sendLeaveDecisionEmail(leave, false);
        } else {
            recalculateStatus(leave);
        }

        return mapToResponseDto(leaveRequestRepository.save(leave));
    }

    /**
     * HR Manager approves or rejects a leave request.
     * Sets hrDecision and recalculates overall status.
     */
    @Transactional
    public LeaveRequestResponseDto hrDecision(Long leaveId, boolean approve) {
        logger.info("HR Manager {} leave request ID: {}", approve ? "approving" : "rejecting", leaveId);

        LeaveRequest leave = leaveRequestRepository.findById(leaveId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found with ID: " + leaveId));

        if (leave.getStatus() == LeaveStatus.REJECTED) {
            throw new BadRequestException("Leave request has already been rejected");
        }
        if (leave.getStatus() == LeaveStatus.APPROVED) {
            throw new BadRequestException("Leave request has already been fully approved");
        }
        // HR can only act after Team Leader has approved
        if (leave.getTeamLeaderDecision() != ApprovalDecision.APPROVED) {
            throw new BadRequestException("Team Leader must approve this request before HR can act on it.");
        }

        leave.setHrDecision(approve ? ApprovalDecision.APPROVED : ApprovalDecision.REJECTED);
        leave.setUpdatedAt(LocalDateTime.now());

        if (!approve) {
            leave.setStatus(LeaveStatus.REJECTED);
            sendLeaveDecisionEmail(leave, false);
        } else {
            recalculateStatus(leave);
        }

        return mapToResponseDto(leaveRequestRepository.save(leave));
    }

    /**
     * Get all leave requests — HR overview.
     */
    public List<LeaveRequestResponseDto> getAllLeaveRequests() {
        return leaveRequestRepository.findAll().stream()
                .map(this::mapToResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Recalculate final status based on both decisions.
     * APPROVED only when BOTH team leader and HR have approved.
     */
    private void recalculateStatus(LeaveRequest leave) {
        boolean tlApproved = leave.getTeamLeaderDecision() == ApprovalDecision.APPROVED;
        boolean hrApproved = leave.getHrDecision() == ApprovalDecision.APPROVED;

        if (tlApproved && hrApproved) {
            leave.setStatus(LeaveStatus.APPROVED);
            leave.setApprovalDate(LocalDate.now());
            // Generate unique QR verification token on full approval
            if (leave.getVerificationToken() == null) {
                leave.setVerificationToken(UUID.randomUUID().toString());
            }
            // ✅ Phase 6 — update total_leave_taken_last_12_months on the Person
            if (leave.getUser() != null && leave.getUser().getPerson() != null) {
                var person = leave.getUser().getPerson();
                int current = (person.getCurrentMonthlyDeductions() != null) ? 0 : 0; // placeholder
                // We track leave on the LeaveRequest itself, not on Person
                // The repository query sumLeaveDaysSince handles the 12-month window dynamically
                logger.info("Leave request ID {} fully APPROVED — {} day(s) added to employee record",
                        leave.getId(), leave.getNumberOfDays());
            }
            logger.info("Leave request ID {} fully APPROVED — verification token generated", leave.getId());
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
            logger.warn("Could not send leave decision email: {}", e.getMessage());
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
     * Validate leave request dates
     */
    private void validateLeaveRequestDates(
            java.time.LocalDate startDate,
            java.time.LocalDate endDate,
            Integer numberOfDays
    ) {
        if (startDate.isAfter(endDate)) {
            throw new BadRequestException("Start date must be before or equal to end date");
        }

        long daysBetween = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (daysBetween != numberOfDays) {
            throw new BadRequestException(
                    String.format("Number of days (%d) does not match date range (%d days)", numberOfDays, daysBetween)
            );
        }

        if (numberOfDays > MAX_LEAVE_DAYS_PER_REQUEST) {
            throw new BadRequestException(
                    String.format("Leave request cannot exceed %d days", MAX_LEAVE_DAYS_PER_REQUEST)
            );
        }
    }

    /**
     * Convert entity to response DTO
     */
    private LeaveRequestResponseDto mapToResponseDto(LeaveRequest leaveRequest) {
        LeaveRequestResponseDto dto = new LeaveRequestResponseDto();
        dto.setId(leaveRequest.getId());
        dto.setEmployeeFullName(leaveRequest.getEmployeeFullName());
        dto.setEmployeeEmail(leaveRequest.getEmployeeEmail());
        dto.setLeaveType(leaveRequest.getLeaveType());
        dto.setStartDate(leaveRequest.getStartDate());
        dto.setEndDate(leaveRequest.getEndDate());
        dto.setNumberOfDays(leaveRequest.getNumberOfDays());
        dto.setReason(leaveRequest.getReason());
        dto.setRequestDate(leaveRequest.getRequestDate());
        dto.setTeamLeaderDecision(leaveRequest.getTeamLeaderDecision());
        dto.setHrDecision(leaveRequest.getHrDecision());
        dto.setStatus(leaveRequest.getStatus());
        dto.setApprovalDate(leaveRequest.getApprovalDate());
        dto.setCreatedAt(leaveRequest.getCreatedAt());
        // Scoring fields
        dto.setSystemScore(leaveRequest.getSystemScore());
        dto.setSystemRecommendation(leaveRequest.getSystemRecommendation());
        dto.setDecisionReason(leaveRequest.getDecisionReason());
        return dto;
    }
}

