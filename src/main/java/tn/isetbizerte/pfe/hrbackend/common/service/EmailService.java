package tn.isetbizerte.pfe.hrbackend.common.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Service for sending email notifications.
 * Handles role assignment notifications and other system emails.
 */
@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@hrbackend.com}")
    private String fromEmail;

    @Value("${app.company.name:HR System}")
    private String companyName;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Sends a role assignment notification email to the user.
     * This method is async to avoid blocking the main request thread.
     *
     * @param toEmail       The recipient's email address
     * @param employeeName  The full name of the employee
     * @param newRole       The new role assigned to the user
     */
    @Async
    public void sendRoleAssignmentEmail(String toEmail, String employeeName, String newRole) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("Role Assignment Notification - " + companyName);
            message.setText(buildRoleAssignmentEmailBody(employeeName, newRole));

            mailSender.send(message);
            logger.info("Role assignment email sent successfully to: {}", toEmail);

        } catch (MailException e) {
            logger.error("Failed to send role assignment email to {}: {}", toEmail, e.getMessage());
            // Don't throw exception - email failure should not break the role assignment flow
        }
    }

    /**
     * Sends a role assignment notification email with detailed user info.
     *
     * @param toEmail       The recipient's email address
     * @param firstName     The first name of the employee
     * @param lastName      The last name of the employee
     * @param newRole       The new role assigned to the user
     * @param username      The username of the user
     */
    @Async
    public void sendRoleAssignmentEmail(String toEmail, String firstName, String lastName,
                                         String newRole, String username) {
        try {
            String employeeName = firstName + " " + lastName;

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("Welcome! Your Role Has Been Assigned - " + companyName);
            message.setText(buildDetailedRoleAssignmentEmailBody(employeeName, newRole, username));

            mailSender.send(message);
            logger.info("Role assignment email sent successfully to: {} ({})", employeeName, toEmail);

        } catch (MailException e) {
            logger.error("Failed to send role assignment email to {}: {}", toEmail, e.getMessage());
            // Don't throw exception - email failure should not break the role assignment flow
        }
    }

    /**
     * Builds the email body for a role assignment notification.
     */
    private String buildRoleAssignmentEmailBody(String employeeName, String newRole) {
        StringBuilder body = new StringBuilder();
        body.append("Dear ").append(employeeName).append(",\n\n");
        body.append("We are pleased to inform you that your role has been successfully assigned in our HR System.\n\n");
        body.append("Your New Role: ").append(formatRoleName(newRole)).append("\n\n");
        body.append("Welcome to the team! You can now access the system with your new permissions.\n");
        body.append("Please log out and log back in to activate your new role.\n\n");
        body.append("If you have any questions, please contact the HR department.\n\n");
        body.append("Best regards,\n");
        body.append(companyName).append(" HR Team");
        return body.toString();
    }

    /**
     * Builds a detailed email body for role assignment notification.
     */
    private String buildDetailedRoleAssignmentEmailBody(String employeeName, String newRole, String username) {
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
     * Converts ENUM_NAME to a readable format.
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

    /**
     * Sends a generic notification email.
     *
     * @param toEmail The recipient's email address
     * @param subject The email subject
     * @param body    The email body
     */
    @Async
    public void sendNotificationEmail(String toEmail, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(body);

            mailSender.send(message);
            logger.info("Notification email sent successfully to: {}", toEmail);

        } catch (MailException e) {
            logger.error("Failed to send notification email to {}: {}", toEmail, e.getMessage());
        }
    }
}

