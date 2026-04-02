package tn.isetbizerte.pfe.hrbackend.modules.calendar.service;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import tn.isetbizerte.pfe.hrbackend.common.enums.TypeRole;
import tn.isetbizerte.pfe.hrbackend.common.enums.LeaveStatus;
import tn.isetbizerte.pfe.hrbackend.common.exception.ResourceNotFoundException;
import tn.isetbizerte.pfe.hrbackend.modules.calendar.dto.CalendarLeaveDto;
import tn.isetbizerte.pfe.hrbackend.modules.employee.entity.LeaveRequest;
import tn.isetbizerte.pfe.hrbackend.modules.employee.repository.LeaveRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.team.entity.Team;
import tn.isetbizerte.pfe.hrbackend.modules.team.repository.TeamRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.UserRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class CalendarLeaveService {

    private final LeaveRequestRepository leaveRepository;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;

    public CalendarLeaveService(LeaveRequestRepository leaveRepository,
                                UserRepository userRepository,
                                TeamRepository teamRepository) {
        this.leaveRepository = leaveRepository;
        this.userRepository = userRepository;
        this.teamRepository = teamRepository;
    }

    @Cacheable(
            value = "calendarLeaves",
            key = "T(java.lang.String).format('%s|%s|%s|%s|%s|%s', #username, #start, #end, #includePending, #employeeId, #statuses)"
    )
    public List<CalendarLeaveDto> getCalendarLeaves(
            String username,
            String keycloakId,
            LocalDate start,
            LocalDate end,
            boolean includePending,
            Long employeeId,
            List<LeaveStatus> statuses
    ) {
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("Start date must be before or equal to end date.");
        }

        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        TypeRole role = getCurrentUserRole(currentUser);
        List<LeaveRequest> leaves;
        List<LeaveStatus> resolvedStatuses = resolveStatuses(statuses, includePending);

        if (role == TypeRole.EMPLOYEE) {
            if (employeeId != null && !employeeId.equals(currentUser.getId())) {
                throw new AccessDeniedException("You are not allowed to access other employees' leaves.");
            }
            leaves = leaveRepository.findByUserAndDateRangeAndStatusIn(currentUser, start, end, resolvedStatuses);
        } else if (role == TypeRole.TEAM_LEADER) {
            Team team = teamRepository.findByTeamLeaderKeycloakId(keycloakId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "No team assigned to this Team Leader. Ask HR to create and assign you a team."));
            if (employeeId != null) {
                leaves = leaveRepository.findByTeamIdAndUserIdAndDateRangeAndStatusIn(team.getId(), employeeId, start, end, resolvedStatuses);
            } else {
                leaves = leaveRepository.findByTeamIdAndDateRangeAndStatusIn(team.getId(), start, end, resolvedStatuses);
            }
        } else if (role == TypeRole.HR_MANAGER) {
            if (employeeId != null) {
                leaves = leaveRepository.findByDateRangeAndStatusInAndUserId(start, end, resolvedStatuses, employeeId);
            } else {
                leaves = leaveRepository.findByDateRangeAndStatusIn(start, end, resolvedStatuses);
            }
        } else {
            throw new AccessDeniedException("You are not allowed to access calendar leaves.");
        }

        return leaves.stream()
                .map(this::mapToCalendarDto)
                .toList();
    }

    private TypeRole getCurrentUserRole(User user) {
        return user.getRole();
    }

    private CalendarLeaveDto mapToCalendarDto(LeaveRequest leave) {
        CalendarLeaveDto dto = new CalendarLeaveDto();
        dto.setLeaveId(leave.getId());
        dto.setStartDate(leave.getStartDate());
        dto.setEndDate(leave.getEndDate());
        dto.setEmployeeId(leave.getUser().getId());
        dto.setEmployeeUsername(leave.getUser().getUsername());
        dto.setEmployeeFullName(leave.getEmployeeFullName());
        dto.setStatus(leave.getStatus().name());
        dto.setLeaveType(leave.getLeaveType().name());
        dto.setReason(leave.getReason());
        dto.setNumberOfDays(leave.getNumberOfDays());
        dto.setTeamLeaderDecision(leave.getTeamLeaderDecision().name());
        dto.setHrDecision(leave.getHrDecision().name());
        dto.setApprovedBy(leave.getApprovedBy());
        dto.setRejectedBy(leave.getRejectedBy());
        return dto;
    }

    private List<LeaveStatus> resolveStatuses(List<LeaveStatus> statusFilter, boolean includePending) {
        if (statusFilter != null && !statusFilter.isEmpty()) {
            return new ArrayList<>(statusFilter);
        }
        List<LeaveStatus> resolved = new ArrayList<>();
        resolved.add(LeaveStatus.APPROVED);
        if (includePending) {
            resolved.add(LeaveStatus.PENDING);
        }
        return resolved;
    }
}
