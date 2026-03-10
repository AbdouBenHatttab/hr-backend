package tn.isetbizerte.pfe.hrbackend.infrastructure.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import tn.isetbizerte.pfe.hrbackend.modules.auth.service.PasswordResetEvent;

import java.time.format.DateTimeFormatter;

/**
 * Service for sending password reset emails with HR Nexus branding
 */
@Service
public class PasswordResetEmailService {

    private static final Logger logger = LoggerFactory.getLogger(PasswordResetEmailService.class);

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.company.name}")
    private String companyName;

    /**
     * Send password reset email with token
     */
    public void sendPasswordResetEmail(PasswordResetEvent event) {
        logger.info("📧 SENDING password reset email to: {} from: {}", event.getEmail(), fromEmail);

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(event.getEmail());
            helper.setSubject("🔐 Password Reset Request - " + companyName);

            String fullName = event.getFirstName() + " " + event.getLastName();
            String emailBody = buildPasswordResetEmailBody(fullName, event.getResetToken(), event.getExpiryTime());
            helper.setText(emailBody, true);

            // Attach logo
            ClassPathResource logo = new ClassPathResource("static/images/logo v1.png");
            if (logo.exists()) {
                helper.addInline("companyLogo", logo);
            }

            mailSender.send(mimeMessage);
            logger.info("✅ Password reset email SENT successfully to: {}", event.getEmail());

        } catch (MessagingException e) {
            logger.error("❌ Failed to send password reset email to: {}", event.getEmail(), e);
            throw new RuntimeException("Failed to send password reset email", e);
        }
    }

    /**
     * Build HTML email body with HR Nexus design
     */
    private String buildPasswordResetEmailBody(String userName, String resetToken, java.time.LocalDateTime expiryTime) {
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        String expiryTimeStr = expiryTime.format(timeFormatter);

        String logoTag = "<img src='cid:companyLogo' alt='" + companyName + "' style='width:96px;height:auto;display:block;margin:0 auto;'/>";

        return """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Password Reset</title>
</head>
<body style="margin:0;padding:0;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'Helvetica Neue',Arial,sans-serif;background-color:#f3f4f6;">
  <table role="presentation" style="width:100%%;border-collapse:collapse;background-color:#f3f4f6;padding:40px 0;">
    <tr>
      <td align="center">
        <table role="presentation" style="max-width:600px;width:100%%;background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 10px 25px rgba(0,0,0,0.1);">
          
          <!-- Header -->
          <tr>
            <td style="background:#1e2a4a;padding:36px 48px 28px;text-align:center;">
              <div style="margin-bottom:20px;">
                %s
              </div>
              <div style="display:inline-block;background:rgba(239,68,68,0.15);border:1px solid rgba(239,68,68,0.4);color:#f87171;font-size:11px;font-weight:700;letter-spacing:2px;text-transform:uppercase;padding:4px 14px;border-radius:20px;margin-bottom:12px;">
                Password Reset
              </div>
              <h1 style="margin:0;color:#ffffff;font-size:22px;font-weight:600;">
                Password Reset Request
              </h1>
            </td>
          </tr>

          <!-- Content -->
          <tr>
            <td style="padding:40px 48px;">
              <p style="margin:0 0 24px;color:#374151;font-size:15px;line-height:1.6;">
                Hello <strong style="color:#1f2937;">%s</strong>,
              </p>
              
              <p style="margin:0 0 24px;color:#374151;font-size:15px;line-height:1.6;">
                We received a request to reset your password for your <strong>%s</strong> account.
              </p>

              <p style="margin:0 0 24px;color:#374151;font-size:15px;line-height:1.6;">
                Please use the following secure token to reset your password:
              </p>

              <!-- Reset Token Box -->
              <div style="background:#f9fafb;border:2px dashed #e5e7eb;border-radius:12px;padding:24px;margin:0 0 24px;text-align:center;">
                <div style="color:#6b7280;font-size:12px;font-weight:600;letter-spacing:1px;text-transform:uppercase;margin-bottom:12px;">
                  Your Reset Token
                </div>
                <div style="background:#ffffff;border:1px solid #e5e7eb;border-radius:8px;padding:16px;font-family:'Courier New',monospace;font-size:18px;font-weight:700;color:#1f2937;letter-spacing:1px;word-break:break-all;">
                  %s
                </div>
                <div style="margin-top:12px;color:#ef4444;font-size:13px;font-weight:500;">
                  ⏰ Expires at: %s
                </div>
              </div>

              <!-- Instructions -->
              <div style="background:#fef3c7;border-left:4px solid #f59e0b;border-radius:8px;padding:16px 20px;margin:0 0 24px;">
                <p style="margin:0;color:#92400e;font-size:13px;line-height:1.5;font-weight:500;">
                  <strong>⚠️ Security Notice:</strong><br>
                  Copy this token and use it in the password reset form. This token is valid for 15 minutes only.
                </p>
              </div>

              <p style="margin:0 0 16px;color:#374151;font-size:15px;line-height:1.6;">
                If you did not request a password reset, please ignore this email or contact support if you have concerns.
              </p>

              <p style="margin:0;color:#6b7280;font-size:14px;line-height:1.6;">
                Best regards,<br>
                <strong style="color:#1f2937;">%s Team</strong>
              </p>
            </td>
          </tr>

          <!-- Footer -->
          <tr>
            <td style="background:#f9fafb;padding:32px 48px;border-top:1px solid #e5e7eb;text-align:center;">
              <p style="margin:0 0 12px;color:#9ca3af;font-size:13px;line-height:1.5;">
                This is an automated message from <strong>%s</strong>
              </p>
              <p style="margin:0;color:#d1d5db;font-size:12px;">
                © 2026 %s. All rights reserved.
              </p>
            </td>
          </tr>

        </table>
      </td>
    </tr>
  </table>
</body>
</html>
                """.formatted(
                logoTag,
                userName,
                companyName,
                resetToken,
                expiryTimeStr,
                companyName,
                companyName,
                companyName
        );
    }
}

