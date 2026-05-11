package tn.isetbizerte.pfe.hrbackend.modules.hr.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.isetbizerte.pfe.hrbackend.modules.hr.dto.SendHrManualEmailRequest;
import tn.isetbizerte.pfe.hrbackend.modules.hr.dto.SendHrManualEmailResponse;
import tn.isetbizerte.pfe.hrbackend.modules.hr.service.HrManualEmailService;

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
}
