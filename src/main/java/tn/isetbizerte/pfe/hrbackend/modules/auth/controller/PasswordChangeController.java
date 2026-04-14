package tn.isetbizerte.pfe.hrbackend.modules.auth.controller;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import tn.isetbizerte.pfe.hrbackend.modules.auth.dto.ChangePasswordWithOldPasswordRequest;
import tn.isetbizerte.pfe.hrbackend.modules.auth.dto.ChangePasswordWithOtpRequest;
import tn.isetbizerte.pfe.hrbackend.modules.auth.service.PasswordChangeService;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/me/password")
public class PasswordChangeController {

    private final PasswordChangeService passwordChangeService;

    public PasswordChangeController(PasswordChangeService passwordChangeService) {
        this.passwordChangeService = passwordChangeService;
    }

    @PreAuthorize("hasAnyRole('EMPLOYEE','TEAM_LEADER')")
    @PostMapping("/otp")
    public Map<String, Object> requestOtp(@AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        passwordChangeService.requestOtp(username);
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("message", "OTP sent to your email");
        return res;
    }

    @PreAuthorize("hasAnyRole('EMPLOYEE','TEAM_LEADER')")
    @PostMapping("/otp/confirm")
    public Map<String, Object> changeWithOtp(@Valid @RequestBody ChangePasswordWithOtpRequest req,
                                             @AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        passwordChangeService.changeWithOtp(username, req.getOtp(), req.getNewPassword());
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("message", "Password changed successfully");
        return res;
    }

    @PreAuthorize("hasAnyRole('EMPLOYEE','TEAM_LEADER')")
    @PostMapping
    public Map<String, Object> changeWithOldPassword(@Valid @RequestBody ChangePasswordWithOldPasswordRequest req,
                                                     @AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        passwordChangeService.changeWithOldPassword(username, req.getOldPassword(), req.getNewPassword());
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("message", "Password changed successfully");
        return res;
    }
}
