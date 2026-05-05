package tn.isetbizerte.pfe.hrbackend.modules.employee.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.isetbizerte.pfe.hrbackend.common.enums.LeaveStatus;
import tn.isetbizerte.pfe.hrbackend.common.enums.LeaveType;
import tn.isetbizerte.pfe.hrbackend.common.enums.TypeRole;
import tn.isetbizerte.pfe.hrbackend.common.exception.BadRequestException;
import tn.isetbizerte.pfe.hrbackend.modules.calendar.dto.WorkingDaysEstimateDto;
import tn.isetbizerte.pfe.hrbackend.modules.calendar.service.WorkingDayService;
import tn.isetbizerte.pfe.hrbackend.modules.employee.dto.LeaveDraftValidationRequestDto;
import tn.isetbizerte.pfe.hrbackend.modules.employee.dto.LeaveDraftValidationResponseDto;
import tn.isetbizerte.pfe.hrbackend.modules.employee.entity.LeaveRequest;
import tn.isetbizerte.pfe.hrbackend.modules.employee.repository.LeaveRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared leave validation logic.
 *
 * Single source of truth for all leave business rules.
 * Called by both:
 *   1. {@link EmployeeLeaveService#createLeaveRequest} — throws exceptions on failure (existing behaviour).
 *   2. {@link LeaveValidationService#validateDraft}    — returns structured result, no side effects.
 *
 * Extraction strategy
 * -------------------
 * The five private methods from EmployeeLeaveService that contain business rules
 * (validateLeaveRequestDates, validateNoOverlappingLeave, validateSickLeaveCreationRules,
 * validateAnnualLeaveCreationRules, validateTeamSetupForLeaveSubmission) are replicated
 * here as package-private throwing methods so EmployeeLeaveService can delegate to them.
 * This avoids any change to EmployeeLeaveService's public contract while establishing
 * a single source of truth going forward.
 *
 * No write operations, no Kafka events, no balance reservations inside this service.
 */
@Service
@Slf4j
@Transactional(readOnly = true)
public class LeaveValidationService {

    static final int MAX_LEAVE_DAYS_PER_REQUEST = 30;
    static final List<LeaveStatus> OVERLAP_BLOCKING_STATUSES = List.of(
            LeaveStatus.PENDING,
            LeaveStatus.APPROVED
    );

    private final WorkingDayService workingDayService;
    private final LeaveBalanceService leaveBalanceService;
    private final LeaveRequestRepository leaveRequestRepository;

    public LeaveValidationService(
            WorkingDayService workingDayService,
            LeaveBalanceService leaveBalanceService,
            LeaveRequestRepository leaveRequestRepository
    ) {
        this.workingDayService      = workingDayService;
        this.leaveBalanceService    = leaveBalanceService;
        this.leaveRequestRepository = leaveRequestRepository;
    }

    // -----------------------------------------------------------------------
    // Public API — dry-run (no side effects)
    // -----------------------------------------------------------------------

    /**
     * Validate a leave draft and return a structured result.
     * Never writes to the database, never reserves balance, never publishes events.
     *
     * @param user the authenticated user submitting the draft
     * @param dto  the draft fields extracted by FastAPI
     * @return a {@link LeaveDraftValidationResponseDto} with valid=true or valid=false
     */
    public LeaveDraftValidationResponseDto validateDraft(User user, LeaveDraftValidationRequestDto dto) {
        List<String> errors   = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // --- 1. Required field checks ---
        if (dto.getLeaveType() == null) {
            errors.add("Leave type is required.");
        }
        if (dto.getStartDate() == null) {
            errors.add("Start date is required.");
        }
        if (dto.getEndDate() == null) {
            errors.add("End date is required.");
        }
        if (dto.getReason() != null && dto.getReason().length() > 1000) {
            errors.add("Reason must not exceed 1000 characters.");
        }

        // If any required field is missing we cannot evaluate further rules
        if (!errors.isEmpty()) {
            return buildInvalidResponse(dto, errors, warnings);
        }

        LocalDate startDate = dto.getStartDate();
        LocalDate endDate   = dto.getEndDate();
        LocalDate today     = LocalDate.now();

        // --- 2. Past-date checks ---
        if (startDate.isBefore(today)) {
            errors.add("Start date cannot be in the past.");
        }
        if (endDate.isBefore(today)) {
            errors.add("End date cannot be in the past.");
        }

        // --- 3. Date order ---
        if (startDate.isAfter(endDate)) {
            errors.add("Start date must be before or equal to end date.");
        }

        // If date order is invalid we cannot compute working days
        if (!errors.isEmpty()) {
            return buildInvalidResponse(dto, errors, warnings);
        }

        // --- 4. Working-day checks (start and end must both be working days) ---
        if (!workingDayService.isWorkingDay(startDate)) {
            errors.add("Leave cannot start on a weekend or public holiday.");
        }
        if (!workingDayService.isWorkingDay(endDate)) {
            errors.add("Leave cannot end on a weekend or public holiday.");
        }

        if (!errors.isEmpty()) {
            return buildInvalidResponse(dto, errors, warnings);
        }

        // --- 5. Working-days count ---
        int workingDays = workingDayService.countWorkingDays(startDate, endDate);
        WorkingDaysEstimateDto breakdown = workingDayService.estimate(startDate, endDate);

        if (workingDays <= 0) {
            errors.add("Leave request must include at least one working day.");
            return buildInvalidResponse(dto, errors, warnings);
        }

        if (workingDays > MAX_LEAVE_DAYS_PER_REQUEST) {
            errors.add(String.format(
                    "Leave request cannot exceed %d working days per request.", MAX_LEAVE_DAYS_PER_REQUEST));
        }

        // --- 6. Team setup check (EMPLOYEE only) ---
        if (user.getRole() == TypeRole.EMPLOYEE) {
            if (user.getTeam() == null) {
                errors.add("You must be assigned to a team before submitting a leave request.");
            } else if (user.getTeam().getTeamLeader() == null) {
                errors.add("Your team has no Team Leader assigned. Ask HR to complete team setup before submitting leave.");
            }
        }

        if (!errors.isEmpty()) {
            return buildInvalidResponse(dto, errors, warnings);
        }

        // --- 7. Overlap check ---
        List<LeaveRequest> overlaps = leaveRequestRepository.findByUserAndDateRangeAndStatusIn(
                user, startDate, endDate, OVERLAP_BLOCKING_STATUSES);
        if (!overlaps.isEmpty()) {
            LeaveRequest conflict = overlaps.get(0);
            errors.add(String.format(
                    "This leave request overlaps with an existing %s leave request from %s to %s.",
                    conflict.getStatus().name().toLowerCase(),
                    conflict.getStartDate(),
                    conflict.getEndDate()
            ));
        }

        // --- 8. Leave-type specific rules ---
        LeaveType leaveType = dto.getLeaveType();

        if (leaveType == LeaveType.SICK) {
            validateSickLeaveRulesDraft(startDate, today, errors);
        }

        if (leaveType == LeaveType.ANNUAL) {
            validateAnnualLeaveRulesDraft(user, startDate, today, workingDays, errors, warnings);
        }

        // --- 9. Build response ---
        boolean isBalanceManaged = leaveBalanceService.isBalanceManaged(leaveType);
        BigDecimal balanceBefore = null;
        BigDecimal balanceAfter  = null;

        if (isBalanceManaged && errors.isEmpty()) {
            balanceBefore = leaveBalanceService.getAvailableDays(user, leaveType, startDate);
            balanceAfter  = balanceBefore.subtract(BigDecimal.valueOf(workingDays));
            if (balanceAfter.compareTo(BigDecimal.ZERO) < 0) {
                balanceAfter = BigDecimal.ZERO;
            }
        }

        String initialStage = (user.getRole() == TypeRole.TEAM_LEADER) ? "PENDING_HR" : "PENDING_TL";

        if (!errors.isEmpty()) {
            LeaveDraftValidationResponseDto result = buildInvalidResponse(dto, errors, warnings);
            result.setWorkingDays(workingDays);
            result.setWorkingDaysBreakdown(breakdown);
            return result;
        }

        LeaveDraftValidationResponseDto result = new LeaveDraftValidationResponseDto();
        result.setValid(true);
        result.setLeaveType(leaveType);
        result.setWorkingDays(workingDays);
        result.setWorkingDaysBreakdown(breakdown);
        result.setBalanceBefore(balanceBefore);
        result.setBalanceAfterIfApproved(balanceAfter);
        result.setBalanceManaged(isBalanceManaged);
        result.setInitialStage(initialStage);
        result.setErrors(new ArrayList<>());
        result.setWarnings(warnings);
        return result;
    }

    // -----------------------------------------------------------------------
    // Throwing validators — used by EmployeeLeaveService (single source of truth)
    // -----------------------------------------------------------------------

    /**
     * Validates dates, working-day boundaries, day count.
     * @return the number of working days between start and end (inclusive)
     * @throws BadRequestException on any violation
     */
    int throwingValidateDates(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new BadRequestException("Start date must be before or equal to end date");
        }
        if (!workingDayService.isWorkingDay(startDate)) {
            throw new BadRequestException("Leave cannot start on a weekend or holiday.");
        }
        if (!workingDayService.isWorkingDay(endDate)) {
            throw new BadRequestException("Leave cannot end on a weekend or holiday.");
        }
        int workingDays = workingDayService.countWorkingDays(startDate, endDate);
        if (workingDays <= 0) {
            throw new BadRequestException("Leave request must include at least one working day.");
        }
        if (workingDays > MAX_LEAVE_DAYS_PER_REQUEST) {
            throw new BadRequestException(
                    String.format("Leave request cannot exceed %d days", MAX_LEAVE_DAYS_PER_REQUEST));
        }
        return workingDays;
    }

    /**
     * Validates no PENDING or APPROVED leave overlaps the given date range.
     * @throws BadRequestException on overlap
     */
    void throwingValidateNoOverlap(User user, LocalDate startDate, LocalDate endDate) {
        throwingValidateNoOverlap(user, startDate, endDate, null);
    }

    /**
     * Validates no PENDING or APPROVED leave overlaps the given date range,
     * excluding the leave with {@code excludedLeaveId} (used for edits).
     * @throws BadRequestException on overlap
     */
    void throwingValidateNoOverlap(User user, LocalDate startDate, LocalDate endDate, Long excludedLeaveId) {
        List<LeaveRequest> overlaps = leaveRequestRepository.findByUserAndDateRangeAndStatusIn(
                user, startDate, endDate, OVERLAP_BLOCKING_STATUSES);
        if (excludedLeaveId != null) {
            overlaps = overlaps.stream()
                    .filter(leave -> leave.getId() == null || !leave.getId().equals(excludedLeaveId))
                    .toList();
        }
        if (overlaps.isEmpty()) return;

        LeaveRequest overlap = overlaps.get(0);
        throw new BadRequestException(String.format(
                "This leave request overlaps with an existing %s leave request from %s to %s.",
                overlap.getStatus().name().toLowerCase(),
                overlap.getStartDate(),
                overlap.getEndDate()
        ));
    }

    /**
     * Validates sick leave advance limit.
     * @throws BadRequestException when start is more than 7 days in the future
     */
    void throwingValidateSickLeave(LocalDate startDate) {
        if (startDate.isAfter(LocalDate.now().plusDays(7))) {
            throw new BadRequestException("Sick leave cannot be requested more than 7 days in advance.");
        }
    }

    /**
     * Validates annual leave start constraints and balance.
     * @throws BadRequestException on any violation
     */
    void throwingValidateAnnualLeave(User user, LocalDate startDate, int requestedWorkingDays) {
        if (!startDate.isAfter(LocalDate.now())) {
            throw new BadRequestException("Annual leave must be requested at least 1 day in advance.");
        }
        if (startDate.isAfter(LocalDate.now().plusMonths(6))) {
            throw new BadRequestException("Annual leave cannot be requested more than 6 months in advance.");
        }
        BigDecimal availableDays = leaveBalanceService.getAvailableDays(user, LeaveType.ANNUAL, startDate);
        if (BigDecimal.valueOf(requestedWorkingDays).compareTo(availableDays) > 0) {
            throw new BadRequestException(String.format(
                    "Insufficient annual leave balance. Requested %d working day(s), but only %s day(s) are available.",
                    requestedWorkingDays,
                    availableDays.stripTrailingZeros().toPlainString()
            ));
        }
    }

    /**
     * Validates team setup for EMPLOYEE submissions.
     * TEAM_LEADER submissions bypass this check.
     * @throws BadRequestException when team or team leader is missing
     */
    void throwingValidateTeamSetup(User user) {
        if (user == null || user.getRole() != TypeRole.EMPLOYEE) {
            return;
        }
        if (user.getTeam() == null) {
            throw new BadRequestException("You must be assigned to a team before submitting a leave request.");
        }
        if (user.getTeam().getTeamLeader() == null) {
            throw new BadRequestException("Your team has no Team Leader assigned. Ask HR to complete team setup before submitting leave.");
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private void validateSickLeaveRulesDraft(LocalDate startDate, LocalDate today, List<String> errors) {
        if (startDate.isAfter(today.plusDays(7))) {
            errors.add("Sick leave cannot be requested more than 7 days in advance.");
        }
    }

    private void validateAnnualLeaveRulesDraft(
            User user,
            LocalDate startDate,
            LocalDate today,
            int workingDays,
            List<String> errors,
            List<String> warnings
    ) {
        if (!startDate.isAfter(today)) {
            errors.add("Annual leave must be requested at least 1 day in advance.");
            return; // no point continuing if date is today or past
        }
        if (startDate.isAfter(today.plusMonths(6))) {
            errors.add("Annual leave cannot be requested more than 6 months in advance.");
            return;
        }
        BigDecimal available = leaveBalanceService.getAvailableDays(user, LeaveType.ANNUAL, startDate);
        if (BigDecimal.valueOf(workingDays).compareTo(available) > 0) {
            errors.add(String.format(
                    "Insufficient annual leave balance. Requested %d working day(s), but only %s day(s) are available.",
                    workingDays,
                    available.stripTrailingZeros().toPlainString()
            ));
        } else if (available.subtract(BigDecimal.valueOf(workingDays)).compareTo(BigDecimal.ZERO) == 0) {
            warnings.add("This request will use your entire remaining annual leave balance.");
        }
    }

    private LeaveDraftValidationResponseDto buildInvalidResponse(
            LeaveDraftValidationRequestDto dto,
            List<String> errors,
            List<String> warnings
    ) {
        LeaveDraftValidationResponseDto result = new LeaveDraftValidationResponseDto();
        result.setValid(false);
        result.setLeaveType(dto.getLeaveType());
        result.setErrors(new ArrayList<>(errors));
        result.setWarnings(new ArrayList<>(warnings));
        return result;
    }
}
