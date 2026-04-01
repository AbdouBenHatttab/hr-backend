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
 * Sends welcome email when HR assigns a role to a new user.
 */
@Service
public class EmailNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(EmailNotificationService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@hrnexus.com}")
    private String fromEmail;

    @Value("${app.company.name:HR Nexus}")
    private String companyName;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    public EmailNotificationService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Sends welcome email when HR activates the user account.
     */
    public void sendRoleAssignmentNotification(UserRoleAssignedEvent event) {

        logger.info("📧 Sending welcome email to {}", event.getEmail());

        try {

            String employeeName = event.getFirstName() + " " + event.getLastName();

            MimeMessage message = mailSender.createMimeMessage();

            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(event.getEmail());
            helper.setSubject("Welcome to HR Nexus – Your account is ready");

            helper.setText(
                    buildWelcomeEmailBody(
                            employeeName,
                            event.getUsername(),
                            frontendUrl + "/login"
                    ),
                    true
            );

            // Attach logo if exists
            ClassPathResource logo = new ClassPathResource("static/images/logo.jpg");
            if (logo.exists()) {
                helper.addInline("companyLogo", logo);
            }

            mailSender.send(message);

            logger.info("✅ Email sent successfully to {}", event.getEmail());

        } catch (MailException e) {
            logger.error("❌ Mail error sending to {} : {}", event.getEmail(), e.getMessage(), e);
        } catch (Exception e) {
            logger.error("❌ Unexpected error sending email to {} : {}", event.getEmail(), e.getMessage(), e);
        }
    }

    /**
     * Builds modern welcome email HTML.
     */
    private String buildWelcomeEmailBody(String employeeName, String username, String loginUrl) {

        boolean logoExists = new ClassPathResource("static/images/logo.jpg").exists();

        String logoTag = logoExists
                ? "<img src='cid:companyLogo' width='140' style='display:block;margin:0 auto;'>"
                : "<span style='color:white;font-size:22px;font-weight:700;'>HR Nexus</span>";

        return """
                <div style="background:#f3f5f9;padding:40px 0;font-family:'Segoe UI',Arial,sans-serif;">

                  <div style="max-width:600px;margin:auto;background:white;border-radius:16px;
                              overflow:hidden;box-shadow:0 10px 40px rgba(0,0,0,0.08);">

                    <!-- HEADER -->
                    <div style="background:#1E3A5F;padding:40px 20px;text-align:center;">
                        %s
                        <h1 style="color:white;margin:20px 0 0;font-size:24px;font-weight:600;">
                            Welcome to HR Nexus
                        </h1>
                        <p style="color:rgba(255,255,255,0.7);margin-top:8px;font-size:14px;">
                            Your account has been successfully activated
                        </p>
                    </div>

                    <!-- BODY -->
                    <div style="padding:40px;">

                        <p style="font-size:16px;color:#334155;margin-bottom:20px;">
                            Hello <strong>%s</strong>,
                        </p>

                        <p style="font-size:15px;color:#475569;line-height:1.7;">
                            We are pleased to inform you that your account is now active on the
                            <strong style="color:#1E3A5F;">HR Nexus platform</strong>.
                        </p>

                        <p style="font-size:15px;color:#475569;line-height:1.7;margin-top:10px;">
                            HR Nexus allows you to manage administrative requests, track tasks,
                            collaborate with your team leader, and stay connected with the HR department.
                        </p>

                        <!-- ACCOUNT CARD -->
                        <div style="background:#f8fafc;border-radius:12px;padding:20px;margin-top:30px;
                                    border:1px solid #e2e8f0;">

                            <p style="font-size:12px;color:#64748b;margin:0;text-transform:uppercase;
                                       letter-spacing:1px;">
                                Account Information
                            </p>

                            <p style="font-size:18px;font-weight:600;color:#1E3A5F;margin-top:6px;">
                                Username: %s
                            </p>

                        </div>

                        <!-- BUTTON -->
                        <div style="text-align:center;margin-top:35px;">

                            <a href="%s"
                               style="background:#22c55e;color:white;text-decoration:none;
                                      padding:14px 28px;border-radius:8px;
                                      font-size:15px;font-weight:600;
                                      display:inline-block;">
                                Access HR Nexus
                            </a>

                        </div>

                        <p style="font-size:14px;color:#64748b;margin-top:30px;text-align:center;">
                            If you have any questions, please contact the HR department.
                        </p>

                    </div>

                    <!-- FOOTER -->
                    <div style="background:#1E3A5F;color:white;padding:22px;text-align:center;">

                        <p style="margin:0;font-size:14px;font-weight:600;">
                            %s
                        </p>

                        <p style="margin-top:6px;font-size:12px;color:rgba(255,255,255,0.6);">
                            Internal Human Resources Management Platform
                        </p>

                        <p style="margin-top:10px;font-size:11px;color:rgba(255,255,255,0.4);">
                            This is an automated message · Please do not reply
                        </p>

                    </div>

                  </div>

                </div>
                """.formatted(
                logoTag,
                employeeName,
                username,
                loginUrl,
                companyName
        );
    }
}