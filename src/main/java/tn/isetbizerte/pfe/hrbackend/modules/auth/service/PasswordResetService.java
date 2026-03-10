package tn.isetbizerte.pfe.hrbackend.modules.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.isetbizerte.pfe.hrbackend.common.exception.BadRequestException;
import tn.isetbizerte.pfe.hrbackend.infrastructure.kafka.producer.PasswordResetKafkaProducer;
import tn.isetbizerte.pfe.hrbackend.modules.hr.service.KeycloakAdminService;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.Person;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.PersonRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Service for handling password reset logic
 */
@Service
public class PasswordResetService {

    private static final Logger logger = LoggerFactory.getLogger(PasswordResetService.class);
    private static final int TOKEN_EXPIRY_MINUTES = 15;

    // In-memory storage for reset tokens (use Redis in production)
    private final Map<String, TokenData> resetTokens = new HashMap<>();

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetKafkaProducer kafkaProducer;

    @Autowired
    private KeycloakAdminService keycloakAdminService;

    /**
     * Initiate password reset process
     * @param email User's email address
     */
    @Transactional
    public void initiatePasswordReset(String email) {
        logger.info("🔐 Password reset initiated for email: {}", email);

        // Find person by email
        Person person = personRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("No account found with email: " + email));

        // Get associated user
        User user = userRepository.findByPerson(person)
                .orElseThrow(() -> new BadRequestException("User account not found for email: " + email));

        // Generate unique reset token
        String resetToken = generateResetToken();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiryTime = now.plusMinutes(TOKEN_EXPIRY_MINUTES);

        // Store token with user info
        resetTokens.put(resetToken, new TokenData(user.getId(), email, expiryTime));

        logger.info("✅ Reset token generated for user: {} (expires in {} minutes)", user.getUsername(), TOKEN_EXPIRY_MINUTES);

        // Publish Kafka event
        PasswordResetEvent event = new PasswordResetEvent(
                email,
                person.getFirstName(),
                person.getLastName(),
                resetToken,
                now,
                expiryTime
        );

        kafkaProducer.publishPasswordResetEvent(event);
        logger.info("📤 Password reset event published to Kafka for: {}", email);
    }

    /**
     * Reset password using token
     * @param token Reset token
     * @param newPassword New password
     */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        logger.info("🔐 Attempting password reset with token");

        // Validate token
        TokenData tokenData = resetTokens.get(token);
        if (tokenData == null) {
            throw new BadRequestException("Invalid or expired reset token");
        }

        // Check if token expired
        if (LocalDateTime.now().isAfter(tokenData.expiryTime)) {
            resetTokens.remove(token);
            throw new BadRequestException("Reset token has expired. Please request a new one.");
        }

        // Find user
        User user = userRepository.findById(tokenData.userId)
                .orElseThrow(() -> new BadRequestException("User not found"));

        // Validate password strength
        validatePassword(newPassword);

        // Update password in Keycloak
        boolean success = keycloakAdminService.resetUserPassword(user.getKeycloakId(), newPassword);

        if (!success) {
            throw new BadRequestException("Failed to reset password in authentication system");
        }

        // Remove used token
        resetTokens.remove(token);

        logger.info("✅ Password successfully reset for user: {}", user.getUsername());
    }

    /**
     * Generate a 6-character random alphanumeric reset token
     * Format: Random mix of uppercase letters and numbers (e.g., A3B7K9, 2X4Y9Z, K5M2P8)
     */
    private String generateResetToken() {
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random random = new Random();
        StringBuilder token = new StringBuilder(6);

        for (int i = 0; i < 6; i++) {
            int index = random.nextInt(characters.length());
            token.append(characters.charAt(index));
        }

        return token.toString();
    }

    /**
     * Validate password strength
     */
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

    /**
     * Inner class to store token data
     */
    private static class TokenData {
        Long userId;
        String email;
        LocalDateTime expiryTime;

        TokenData(Long userId, String email, LocalDateTime expiryTime) {
            this.userId = userId;
            this.email = email;
            this.expiryTime = expiryTime;
        }
    }
}

