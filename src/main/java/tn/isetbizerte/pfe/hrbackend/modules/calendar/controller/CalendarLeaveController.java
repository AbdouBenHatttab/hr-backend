package tn.isetbizerte.pfe.hrbackend.modules.calendar.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import tn.isetbizerte.pfe.hrbackend.modules.calendar.dto.CalendarLeaveDto;
import tn.isetbizerte.pfe.hrbackend.modules.calendar.service.CalendarLeaveService;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/calendar")
public class CalendarLeaveController {

    private final CalendarLeaveService calendarLeaveService;

    public CalendarLeaveController(CalendarLeaveService calendarLeaveService) {
        this.calendarLeaveService = calendarLeaveService;
    }

    @PreAuthorize("hasAnyRole('EMPLOYEE','TEAM_LEADER','HR_MANAGER')")
    @GetMapping("/leaves")
    public ResponseEntity<Map<String, Object>> getCalendarLeaves(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate end,
            @RequestParam(defaultValue = "false") boolean includePending,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) String status
    ) {
        String username = jwt.getClaimAsString("preferred_username");
        String keycloakId = jwt.getSubject();

        List<CalendarLeaveDto> leaves = calendarLeaveService.getCalendarLeaves(
                username,
                keycloakId,
                start,
                end,
                includePending,
                employeeId,
                parseStatuses(status)
        );

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Calendar leaves retrieved successfully");
        response.put("start", start);
        response.put("end", end);
        response.put("count", leaves.size());
        response.put("data", leaves);

        return ResponseEntity.ok(response);
    }

    private List<tn.isetbizerte.pfe.hrbackend.common.enums.LeaveStatus> parseStatuses(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String[] tokens = status.split(",");
        List<tn.isetbizerte.pfe.hrbackend.common.enums.LeaveStatus> result = new java.util.ArrayList<>();
        for (String token : tokens) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) continue;
            result.add(tn.isetbizerte.pfe.hrbackend.common.enums.LeaveStatus.valueOf(trimmed.toUpperCase()));
        }
        return result.isEmpty() ? null : result;
    }
}
