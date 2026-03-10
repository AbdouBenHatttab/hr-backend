package tn.isetbizerte.pfe.hrbackend.modules.auth.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.isetbizerte.pfe.hrbackend.modules.auth.dto.ForgotPasswordRequest;
import tn.isetbizerte.pfe.hrbackend.modules.auth.dto.ResetPasswordRequest;
import tn.isetbizerte.pfe.hrbackend.modules.auth.service.PasswordResetService;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for password reset endpoints
 */
@RestController
@RequestMapping("/public/auth")
public class PasswordResetController {

    private static final Logger logger = LoggerFactory.getLogger(PasswordResetController.class);

    @Autowired
    private PasswordResetService passwordResetService;

    /**
     * Endpoint to request password reset
     * POST /public/auth/forgot-password
     * Body: { "email": "user@example.com" }
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, Object>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        logger.info("📧 Forgot password request for email: {}", request.getEmail());

        passwordResetService.initiatePasswordReset(request.getEmail());

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "If an account exists with this email, you will receive password reset instructions.");

        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint to reset password with token
     * POST /public/auth/reset-password
     * Body: { "token": "abc123...", "newPassword": "NewPass123!" }
     */
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        logger.info("🔐 Password reset attempt with token");

        passwordResetService.resetPassword(request.getToken(), request.getNewPassword());

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Password has been successfully reset. You can now login with your new password.");

        return ResponseEntity.ok(response);
    }
}

