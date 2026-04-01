package tn.isetbizerte.pfe.hrbackend.modules.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.isetbizerte.pfe.hrbackend.common.event.PasswordResetEvent;
import tn.isetbizerte.pfe.hrbackend.common.exception.BadRequestException;
import tn.isetbizerte.pfe.hrbackend.infrastructure.kafka.producer.PasswordResetKafkaProducer;
import tn.isetbizerte.pfe.hrbackend.modules.auth.entity.PasswordResetToken;
import tn.isetbizerte.pfe.hrbackend.modules.auth.repository.PasswordResetTokenRepository;
import tn.isetbizerte.pfe.hrbackend.modules.hr.service.KeycloakAdminService;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.Person;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.PersonRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.UserRepository;

import java.security.SecureRandom;
import java.time.LocalDateTime;

/**
 * Service for handling password reset logic
 */
@Service
public class PasswordResetService {

    private static final Logger logger = LoggerFactory.getLogger(PasswordResetService.class);
    private static final int TOKEN_EXPIRY_MINUTES = 15;
    private static final String TOKEN_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int TOKEN_LENGTH = 6;

    private final PersonRepository personRepository;
    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordResetKafkaProducer kafkaProducer;
    private final KeycloakAdminService keycloakAdminService;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(
            PersonRepository personRepository,
            UserRepository userRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PasswordResetKafkaProducer kafkaProducer,
            KeycloakAdminService keycloakAdminService
    ) {
        this.personRepository = personRepository;
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.kafkaProducer = kafkaProducer;
        this.keycloakAdminService = keycloakAdminService;
    }

    /**
     * Initiate password reset process
     * @param email User's email address
     */
    @Transactional
    public void initiatePasswordReset(String email) {
        logger.info("Password reset initiated for email: {}", email);

        // Silent return if email not found — prevents user enumeration
        java.util.Optional<Person> personOpt = personRepository.findByEmail(email);
        if (personOpt.isEmpty()) {
            logger.info("Password reset requested for unknown email (silently ignored): {}", email);
            return;
        }
        Person person = personOpt.get();

        java.util.Optional<User> userOpt = userRepository.findByPerson(person);
        if (userOpt.isEmpty()) {
            logger.warn("Person found but no linked user for email: {}", email);
            return;
        }
        User user = userOpt.get();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiryTime = now.plusMinutes(TOKEN_EXPIRY_MINUTES);
        String resetToken = generateUniqueResetToken();

        // Keep only one valid token per user.
        passwordResetTokenRepository.deleteByUserId(user.getId());

        PasswordResetToken tokenEntity = new PasswordResetToken(
                resetToken,
                user.getId(),
                email,
                now,
                expiryTime
        );
        passwordResetTokenRepository.save(tokenEntity);

        logger.info("Reset token persisted for user: {} (expires in {} minutes)", user.getUsername(), TOKEN_EXPIRY_MINUTES);

        PasswordResetEvent event = new PasswordResetEvent(
                email,
                person.getFirstName(),
                person.getLastName(),
                resetToken,
                now,
                expiryTime
        );

        kafkaProducer.publishPasswordResetEvent(event);
        logger.info("Password reset event published to Kafka for: {}", email);

        int cleanedRows = passwordResetTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now());
        if (cleanedRows > 0) {
            logger.debug("Cleaned {} expired password reset token(s)", cleanedRows);
        }
    }

    /**
     * Reset password using token
     * @param token Reset token
     * @param newPassword New password
     */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        logger.info("Attempting password reset with token");

        PasswordResetToken tokenData = passwordResetTokenRepository.findByTokenAndUsedFalse(token)
                .orElseThrow(() -> new BadRequestException("Invalid or expired reset token"));

        if (LocalDateTime.now().isAfter(tokenData.getExpiresAt())) {
            passwordResetTokenRepository.delete(tokenData);
            throw new BadRequestException("Reset token has expired. Please request a new one.");
        }

        User user = userRepository.findById(tokenData.getUserId())
                .orElseThrow(() -> new BadRequestException("User not found"));

        validatePassword(newPassword);

        boolean success = keycloakAdminService.resetUserPassword(user.getKeycloakId(), newPassword);
        if (!success) {
            throw new BadRequestException("Failed to reset password in authentication system");
        }

        tokenData.setUsed(true);
        passwordResetTokenRepository.save(tokenData);

        logger.info("Password successfully reset for user: {}", user.getUsername());
    }

    /**
     * Generate a 6-character random alphanumeric reset token
     */
    private String generateUniqueResetToken() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String candidate = generateResetToken();
            if (!passwordResetTokenRepository.existsByToken(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate a unique reset token");
    }

    private String generateResetToken() {
        StringBuilder token = new StringBuilder(TOKEN_LENGTH);
        for (int i = 0; i < TOKEN_LENGTH; i++) {
            int index = secureRandom.nextInt(TOKEN_CHARACTERS.length());
            token.append(TOKEN_CHARACTERS.charAt(index));
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
}
