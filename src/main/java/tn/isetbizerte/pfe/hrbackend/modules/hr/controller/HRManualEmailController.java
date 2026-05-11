package tn.isetbizerte.pfe.hrbackend.modules.hr.controller;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import tn.isetbizerte.pfe.hrbackend.modules.hr.dto.HrManualEmailLogResponse;
import tn.isetbizerte.pfe.hrbackend.modules.hr.dto.HrManualEmailLogsResponse;
import tn.isetbizerte.pfe.hrbackend.modules.hr.dto.SendHrManualEmailRequest;
import tn.isetbizerte.pfe.hrbackend.modules.hr.dto.SendHrManualEmailResponse;
import tn.isetbizerte.pfe.hrbackend.modules.hr.service.HrManualEmailService;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/hr/emails")
public class HRManualEmailController {

    private final HrManualEmailService hrManualEmailService;

    public HRManualEmailController(HrManualEmailService hrManualEmailService) {
        this.hrManualEmailService = hrManualEmailService;
    }

    @PreAuthorize("hasRole('HR_MANAGER')")
    @PostMapping("/send")
    public ResponseEntity<SendHrManualEmailResponse> sendManualEmail(
            @Valid @RequestBody SendHrManualEmailRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.ok(hrManualEmailService.sendManualEmail(request, jwt));
    }

    @PreAuthorize("hasRole('HR_MANAGER')")
    @GetMapping("/logs")
    public ResponseEntity<HrManualEmailLogsResponse> getManualEmailLogs(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String recipient,
            @RequestParam(required = false) String sender,
            @RequestParam(required = false) String referenceType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {

        Page<HrManualEmailLogResponse> logs = hrManualEmailService.getManualEmailLogs(
                jwt,
                status,
                recipient,
                sender,
                referenceType,
                dateFrom,
                dateTo,
                PageRequest.of(page, size)
        );

        HrManualEmailLogsResponse response = new HrManualEmailLogsResponse();
        response.setMessage("Manual HR email logs retrieved successfully");
        response.setRequestedBy(jwt.getClaimAsString("preferred_username"));
        response.setTotalCount(logs.getTotalElements());
        response.setTotalPages(logs.getTotalPages());
        response.setPage(page);
        response.setSize(size);
        response.setLogs(logs.getContent());

        return ResponseEntity.ok(response);
    }
}
