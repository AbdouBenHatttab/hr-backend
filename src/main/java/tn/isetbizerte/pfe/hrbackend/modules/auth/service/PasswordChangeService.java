package tn.isetbizerte.pfe.hrbackend.modules.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import tn.isetbizerte.pfe.hrbackend.common.exception.BadRequestException;
import tn.isetbizerte.pfe.hrbackend.common.exception.ResourceNotFoundException;
import tn.isetbizerte.pfe.hrbackend.infrastructure.email.HREmailService;
import tn.isetbizerte.pfe.hrbackend.modules.auth.entity.PasswordChangeToken;
import tn.isetbizerte.pfe.hrbackend.modules.auth.repository.PasswordChangeTokenRepository;
import tn.isetbizerte.pfe.hrbackend.modules.hr.service.KeycloakAdminService;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.Person;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.UserRepository;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
public class PasswordChangeService {

    private static final Logger logger = LoggerFactory.getLogger(PasswordChangeService.class);
    private static final int TOKEN_EXPIRY_MINUTES = 15;
    private static final String TOKEN_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int TOKEN_LENGTH = 6;

    @Value("${keycloak.auth-server-url:http://localhost:8080}")
    private String keycloakServerUrl;

    @Value("${keycloak.realm:hr-realm}")
    private String realm;

    @Value("${keycloak.client-id:hr-backend}")
    private String clientId;

    @Value("${keycloak.client-secret:}")
    private String clientSecret;

    private final PasswordChangeTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final KeycloakAdminService keycloakAdminService;
    private final HREmailService hrEmailService;
    private final RestTemplate restTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordChangeService(
            PasswordChangeTokenRepository tokenRepository,
            UserRepository userRepository,
            KeycloakAdminService keycloakAdminService,
            HREmailService hrEmailService,
            RestTemplate restTemplate
    ) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.keycloakAdminService = keycloakAdminService;
        this.hrEmailService = hrEmailService;
        this.restTemplate = restTemplate;
    }

    @Transactional
    public void requestOtp(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Person person = user.getPerson();
        if (person == null || person.getEmail() == null || person.getEmail().isBlank()) {
            throw new BadRequestException("No email address found for this account");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiryTime = now.plusMinutes(TOKEN_EXPIRY_MINUTES);
        String otp = generateUniqueToken();

        // One active token per user.
        tokenRepository.deleteByUserId(user.getId());
        tokenRepository.save(new PasswordChangeToken(otp, user.getId(), person.getEmail(), now, expiryTime));
        tokenRepository.deleteByExpiresAtBefore(LocalDateTime.now());

        String expiresAt = expiryTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm 'on' dd/MM/yyyy"));
        hrEmailService.sendPasswordChangeOtp(person.getEmail(), person.getFirstName(), person.getLastName(), otp, expiresAt);
        logger.info("Password change OTP generated for user '{}'", username);
    }

    @Transactional
    public void changeWithOtp(String username, String otp, String newPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        PasswordChangeToken tokenData = tokenRepository.findByTokenAndUsedFalse(otp)
                .orElseThrow(() -> new BadRequestException("Invalid or expired OTP"));

        if (!tokenData.getUserId().equals(user.getId())) {
            throw new BadRequestException("Invalid or expired OTP");
        }
        if (LocalDateTime.now().isAfter(tokenData.getExpiresAt())) {
            tokenRepository.delete(tokenData);
            throw new BadRequestException("OTP has expired. Please request a new one.");
        }

        validatePassword(newPassword);

        boolean success = keycloakAdminService.resetUserPassword(user.getKeycloakId(), newPassword);
        if (!success) {
            throw new BadRequestException("Failed to change password in authentication system");
        }

        tokenData.setUsed(true);
        tokenRepository.save(tokenData);
    }

    @Transactional
    public void changeWithOldPassword(String username, String oldPassword, String newPassword) {
        if (oldPassword == null || oldPassword.isBlank()) {
            throw new BadRequestException("Old password is required");
        }
        validatePassword(newPassword);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!validateCredentialsWithKeycloak(username, oldPassword)) {
            throw new BadRequestException("Old password is incorrect");
        }

        boolean success = keycloakAdminService.resetUserPassword(user.getKeycloakId(), newPassword);
        if (!success) {
            throw new BadRequestException("Failed to change password in authentication system");
        }
    }

    private boolean validateCredentialsWithKeycloak(String username, String password) {
        String tokenUrl = keycloakServerUrl + "/realms/" + realm + "/protocol/openid-connect/token";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("client_id", clientId);
            if (clientSecret != null && !clientSecret.isEmpty()) {
                body.add("client_secret", clientSecret);
            }
            body.add("grant_type", "password");
            body.add("username", username);
            body.add("password", password);
            body.add("scope", "openid");

            ResponseEntity<Object> response = restTemplate.postForEntity(tokenUrl, new HttpEntity<>(body, headers), Object.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (RestClientResponseException e) {
            return false;
        } catch (RestClientException e) {
            throw new BadRequestException("Authentication service unreachable. Please try again.");
        }
    }

    private String generateUniqueToken() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String candidate = generateToken();
            if (!tokenRepository.existsByToken(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate a unique OTP");
    }

    private String generateToken() {
        StringBuilder token = new StringBuilder(TOKEN_LENGTH);
        for (int i = 0; i < TOKEN_LENGTH; i++) {
            int index = secureRandom.nextInt(TOKEN_CHARACTERS.length());
            token.append(TOKEN_CHARACTERS.charAt(index));
        }
        return token.toString();
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8) {
            throw new BadRequestException("Password must be at least 8 characters long");
        }
        if (!password.matches(".*[A-Z].*")) {
            throw new BadRequestException("Password must contain at least one uppercase letter");
        }
        if (!password.matches(".*[a-z].*")) {
            throw new BadRequestException("Password must contain at least one lowercase letter");
        }
        if (!password.matches(".*\\d.*")) {
            throw new BadRequestException("Password must contain at least one digit");
        }
    }
}
