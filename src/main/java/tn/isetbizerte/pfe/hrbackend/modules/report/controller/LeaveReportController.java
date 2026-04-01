package tn.isetbizerte.pfe.hrbackend.modules.report.controller;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.isetbizerte.pfe.hrbackend.modules.report.dto.LeaveApprovalDocument;
import tn.isetbizerte.pfe.hrbackend.modules.report.service.LeaveReportApplicationService;

@RestController
@RequestMapping("/api/reports")
public class LeaveReportController {

    private final LeaveReportApplicationService leaveReportApplicationService;

    public LeaveReportController(LeaveReportApplicationService leaveReportApplicationService) {
        this.leaveReportApplicationService = leaveReportApplicationService;
    }

    @PreAuthorize("hasAnyRole('EMPLOYEE','TEAM_LEADER','HR_MANAGER')")
    @GetMapping("/leave/{leaveId}")
    public ResponseEntity<byte[]> getLeaveApprovalReport(
            @PathVariable Long leaveId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String requesterKeycloakId = jwt.getSubject();
        LeaveApprovalDocument document = leaveReportApplicationService.generateLeaveApprovalDocument(leaveId, requesterKeycloakId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename(document.getFileName()).build());

        return ResponseEntity.ok()
                .headers(headers)
                .body(document.getContent());
    }
}

