package tn.isetbizerte.pfe.hrbackend.infrastructure.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import tn.isetbizerte.pfe.hrbackend.common.event.UserRoleAssignedEvent;

/**
 * Email notification service in infrastructure layer.
 * Handles sending email notifications triggered by Kafka events.
 */
@Service
public class EmailNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(EmailNotificationService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@hrbackend.com}")
    private String fromEmail;

    @Value("${app.company.name:HR System}")
    private String companyName;

    public EmailNotificationService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Sends role assignment notification email based on Kafka event.
     */
    public void sendRoleAssignmentNotification(UserRoleAssignedEvent event) {
        logger.info("📧 SENDING EMAIL to: {} from: {}", event.getEmail(), fromEmail);
        try {
            String employeeName = event.getFirstName() + " " + event.getLastName();

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(event.getEmail());
            message.setSubject("Welcome! Your Role Has Been Assigned - " + companyName);
            message.setText(buildRoleAssignmentEmailBody(employeeName, event.getNewRole(), event.getUsername()));

            mailSender.send(message);
            logger.info("✅ Email SENT successfully to: {} ({})", employeeName, event.getEmail());

        } catch (MailException e) {
            logger.error("❌ MAIL ERROR to {}: {}", event.getEmail(), e.getMessage(), e);
        } catch (Exception e) {
            logger.error("❌ UNEXPECTED ERROR sending email to {}: {}", event.getEmail(), e.getMessage(), e);
        }
    }

    /**
     * Builds the email body for role assignment notification.
     */
    private String buildRoleAssignmentEmailBody(String employeeName, String newRole, String username) {
        StringBuilder body = new StringBuilder();
        body.append("Dear ").append(employeeName).append(",\n\n");
        body.append("Welcome to ").append(companyName).append("!\n\n");
        body.append("We are pleased to inform you that your account has been approved and ");
        body.append("your role has been assigned in our HR System.\n\n");
        body.append("═══════════════════════════════════════\n");
        body.append("Account Details:\n");
        body.append("───────────────────────────────────────\n");
        body.append("Username: ").append(username).append("\n");
        body.append("Assigned Role: ").append(formatRoleName(newRole)).append("\n");
        body.append("═══════════════════════════════════════\n\n");
        body.append("What's next?\n");
        body.append("• Please log out and log back in to activate your new role\n");
        body.append("• You can now access features available to your role\n");
        body.append("• Contact HR if you have any questions about your responsibilities\n\n");
        body.append("If you did not expect this email or have any concerns, ");
        body.append("please contact the HR department immediately.\n\n");
        body.append("Best regards,\n");
        body.append("The ").append(companyName).append(" HR Team\n\n");
        body.append("---\n");
        body.append("This is an automated message. Please do not reply directly to this email.");
        return body.toString();
    }

    /**
     * Formats the role name for display in emails.
     */
    private String formatRoleName(String role) {
        if (role == null) return "Unknown";

        return switch (role.toUpperCase()) {
            case "EMPLOYEE" -> "Employee";
            case "TEAM_LEADER" -> "Team Leader";
            case "HR_MANAGER" -> "HR Manager";
            case "NEW_USER" -> "New User (Pending)";
            default -> role;
        };
    }
}

