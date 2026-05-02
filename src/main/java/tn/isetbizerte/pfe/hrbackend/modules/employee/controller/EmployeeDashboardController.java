package tn.isetbizerte.pfe.hrbackend.modules.employee.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.isetbizerte.pfe.hrbackend.modules.hr.service.DashboardRequestSummaryService;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/employee/dashboard")
public class EmployeeDashboardController {

    private final DashboardRequestSummaryService dashboardRequestSummaryService;

    public EmployeeDashboardController(DashboardRequestSummaryService dashboardRequestSummaryService) {
        this.dashboardRequestSummaryService = dashboardRequestSummaryService;
    }

    @PreAuthorize("hasAnyRole('EMPLOYEE', 'TEAM_LEADER')")
    @GetMapping("/summary")
    public Map<String, Object> getDashboardSummary(@AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("openRequests", dashboardRequestSummaryService.getEmployeeOpenRequestsSummary(jwt.getSubject()));
        return response;
    }
}
