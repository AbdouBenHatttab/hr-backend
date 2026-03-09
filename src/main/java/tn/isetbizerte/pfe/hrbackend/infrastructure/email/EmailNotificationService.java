package tn.isetbizerte.pfe.hrbackend.infrastructure.email;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
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

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(event.getEmail());
            helper.setSubject("Welcome to HR Nexus! Your Role Has Been Assigned");
            helper.setText(buildRoleAssignmentEmailBody(employeeName, event.getNewRole(), event.getUsername()), true);

            // Attach logo as inline image if it exists
            ClassPathResource logo = new ClassPathResource("static/images/logo v1.png");
            if (logo.exists()) {
                helper.addInline("companyLogo", logo);
            }

            mailSender.send(message);
            logger.info("✅Email SENT successfully to: {} ({})", employeeName, event.getEmail());

        } catch (MailException e) {
            logger.error("❌ MAIL ERROR to {}: {}", event.getEmail(), e.getMessage(), e);
        } catch (Exception e) {
            logger.error("❌ UNEXPECTED ERROR sending email to {}: {}", event.getEmail(), e.getMessage(), e);
        }
    }

    /**
     * Builds the HTML email body for role assignment notification.
     * Design matches HR Nexus app: navy blue (#1e2a4a) + white + green accent.
     */
    private String buildRoleAssignmentEmailBody(String employeeName, String newRole, String username) {
        // If logo file exists use cid:companyLogo, otherwise show text
        boolean logoExists = new ClassPathResource("static/images/logo v1.png").exists();
        String logoTag = logoExists
                ? "<img src='cid:companyLogo' alt='HR Nexus' width='130' style='display:block;margin:0 auto;max-height:50px;object-fit:contain;'/>"
                : "<span style='color:#ffffff;font-size:20px;font-weight:700;letter-spacing:1px;'>HR Nexus</span>";

        return """
            <div style="margin:0;padding:0;background:#f4f6f9;font-family:'Segoe UI',Helvetica,Arial,sans-serif;">
              <div style="max-width:580px;margin:32px auto;border-radius:16px;overflow:hidden;box-shadow:0 8px 32px rgba(30,42,74,0.13);">

                <!-- TOP BAR -->
                <div style="background:#1e2a4a;padding:0;height:4px;"></div>

                <!-- HEADER -->
                <div style="background:#1e2a4a;padding:36px 48px 28px;text-align:center;">
                  <div style="margin-bottom:20px;">
                    %s
                  </div>
                  <div style="display:inline-block;background:rgba(34,197,94,0.15);border:1px solid rgba(34,197,94,0.4);color:#4ade80;font-size:11px;font-weight:700;letter-spacing:2px;text-transform:uppercase;padding:4px 14px;border-radius:20px;margin-bottom:12px;">
                    Account Activated
                  </div>
                  <h1 style="margin:0;color:#ffffff;font-size:22px;font-weight:600;">
                    Your Role Has Been Assigned
                  </h1>
                  <p style="margin:8px 0 0;color:rgba(255,255,255,0.5);font-size:13px;">
                    Welcome to the HR Nexus platform
                  </p>
                </div>

                <!-- GREETING BANNER -->
                <div style="background:#253558;padding:20px 48px;border-bottom:1px solid #1e2a4a;">
                  <p style="margin:0;color:rgba(255,255,255,0.9);font-size:14px;">
                    Good day, <strong style="color:#ffffff;">%s</strong>
                  </p>
                  <p style="margin:4px 0 0;color:rgba(255,255,255,0.5);font-size:12px;">
                    Your account is now active and ready to use
                  </p>
                </div>

                <!-- BODY -->
                <div style="background:#ffffff;padding:36px 48px;">
                  <p style="color:#4a5568;font-size:14px;line-height:1.8;margin:0 0 24px;">
                    We are pleased to inform you that your account has been approved on <strong style="color:#1e2a4a;">%s</strong> and your role has been successfully configured by the HR team.
                  </p>

                  <!-- ACCOUNT DETAILS CARD -->
                  <div style="background:#f8fafc;border:1px solid #e2e8f0;border-radius:12px;overflow:hidden;margin-bottom:28px;">
                    <div style="background:#1e2a4a;padding:10px 20px;">
                      <p style="margin:0;font-size:11px;font-weight:700;letter-spacing:2px;text-transform:uppercase;color:rgba(255,255,255,0.6);">
                        Account Details
                      </p>
                    </div>
                    <div style="padding:0 20px;">
                      <div style="padding:16px 0;border-bottom:1px solid #e2e8f0;">
                        <p style="margin:0;font-size:11px;color:#94a3b8;text-transform:uppercase;letter-spacing:1px;">Username</p>
                        <p style="margin:4px 0 0;font-size:15px;font-weight:600;color:#1e2a4a;">%s</p>
                      </div>
                      <div style="padding:16px 0;">
                        <p style="margin:0;font-size:11px;color:#94a3b8;text-transform:uppercase;letter-spacing:1px;">Assigned Role</p>
                        <span style="display:inline-block;margin-top:6px;background:#22c55e;color:#ffffff;font-size:12px;font-weight:700;padding:4px 14px;border-radius:20px;letter-spacing:0.5px;">
                          %s
                        </span>
                      </div>
                    </div>
                  </div>

                  <!-- NEXT STEPS -->
                  <p style="color:#1e2a4a;font-size:14px;font-weight:700;margin:0 0 16px;">What's next?</p>
                  <div style="margin-bottom:12px;">
                    <table style="width:100%%;border-collapse:collapse;"><tr>
                      <td style="width:32px;vertical-align:top;padding-top:2px;">
                        <div style="width:26px;height:26px;background:#1e2a4a;color:#ffffff;border-radius:50%%;text-align:center;font-size:12px;font-weight:700;line-height:26px;">1</div>
                      </td>
                      <td style="font-size:14px;color:#4a5568;line-height:1.6;padding-left:8px;">
                        <strong style="color:#1e2a4a;">Log out and log back in</strong> to activate your new role and permissions
                      </td>
                    </tr></table>
                  </div>
                  <div style="margin-bottom:12px;">
                    <table style="width:100%%;border-collapse:collapse;"><tr>
                      <td style="width:32px;vertical-align:top;padding-top:2px;">
                        <div style="width:26px;height:26px;background:#1e2a4a;color:#ffffff;border-radius:50%%;text-align:center;font-size:12px;font-weight:700;line-height:26px;">2</div>
                      </td>
                      <td style="font-size:14px;color:#4a5568;line-height:1.6;padding-left:8px;">
                        Access your <strong style="color:#1e2a4a;">Dashboard</strong> to view your tasks, leave balance, and notifications
                      </td>
                    </tr></table>
                  </div>
                  <div style="margin-bottom:28px;">
                    <table style="width:100%%;border-collapse:collapse;"><tr>
                      <td style="width:32px;vertical-align:top;padding-top:2px;">
                        <div style="width:26px;height:26px;background:#1e2a4a;color:#ffffff;border-radius:50%%;text-align:center;font-size:12px;font-weight:700;line-height:26px;">3</div>
                      </td>
                      <td style="font-size:14px;color:#4a5568;line-height:1.6;padding-left:8px;">
                        Contact <strong style="color:#1e2a4a;">HR</strong> if you have any questions about your responsibilities
                      </td>
                    </tr></table>
                  </div>

                  <!-- NOTICE -->
                  <div style="padding:14px 18px;background:#fffbeb;border-left:3px solid #f59e0b;border-radius:0 8px 8px 0;">
                    <p style="margin:0;font-size:13px;color:#92400e;line-height:1.6;">
                      If you did not expect this email or have any concerns, please contact the HR department immediately.
                    </p>
                  </div>
                </div>

                <!-- FOOTER -->
                <div style="background:#1e2a4a;padding:24px 48px;text-align:center;">
                  <p style="margin:0 0 6px;color:#ffffff;font-size:14px;font-weight:600;">%s</p>
                  <p style="margin:0;color:rgba(255,255,255,0.4);font-size:12px;">
                    This is an automated message · Please do not reply to this email
                  </p>
                </div>

                <!-- BOTTOM BAR -->
                <div style="background:#22c55e;height:4px;"></div>

              </div>
            </div>
            """.formatted(logoTag, employeeName, companyName, username, formatRoleName(newRole), companyName);
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
