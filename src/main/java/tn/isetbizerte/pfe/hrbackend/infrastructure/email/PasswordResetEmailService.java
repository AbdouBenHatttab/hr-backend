package tn.isetbizerte.pfe.hrbackend.infrastructure.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import tn.isetbizerte.pfe.hrbackend.common.event.PasswordResetEvent;

import java.time.format.DateTimeFormatter;

@Service
public class PasswordResetEmailService {

    private static final Logger logger = LoggerFactory.getLogger(PasswordResetEmailService.class);

    private final JavaMailSender mailSender;
    private final String fromEmail;
    private final String companyName;

    public PasswordResetEmailService(
            JavaMailSender mailSender,
            @Value("${spring.mail.username}") String fromEmail,
            @Value("${app.company.name:ArabSoft}") String companyName
    ) {
        this.mailSender = mailSender;
        this.fromEmail = fromEmail;
        this.companyName = companyName;
    }

    public void sendPasswordResetEmail(PasswordResetEvent event) {
        logger.info("Sending password reset email to '{}'", event.getEmail());

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(event.getEmail());
            helper.setSubject("🔐 Password Reset Code – " + companyName);

            String fullName = event.getFirstName() + " " + event.getLastName();

            String emailBody = buildPasswordResetEmailBody(
                    fullName,
                    event.getResetToken(),
                    event.getExpiryTime()
            );

            helper.setText(emailBody, true);

            ClassPathResource logo = new ClassPathResource("static/images/logo.jpg");

            if (logo.exists()) {
                helper.addInline("companyLogo", logo);
            }

            mailSender.send(mimeMessage);

            logger.info("Password reset email sent successfully to '{}'", event.getEmail());

        } catch (MessagingException e) {
            logger.error("Failed to send password reset email to '{}'", event.getEmail(), e);
            throw new RuntimeException("Failed to send password reset email", e);
        }
    }

    private String buildPasswordResetEmailBody(
            String userName,
            String resetToken,
            java.time.LocalDateTime expiryTime
    ) {

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");

        String expiry = expiryTime.format(formatter);

        String logoTag = "<img src='cid:companyLogo' width='120' style='display:block;margin:0 auto;'>";

        return """
                <div style="background:#f3f5f9;padding:40px 0;font-family:'Segoe UI',Arial,sans-serif;">

                  <div style="max-width:600px;margin:auto;background:white;border-radius:16px;
                              overflow:hidden;box-shadow:0 10px 40px rgba(0,0,0,0.08);">

                    <!-- HEADER -->
                    <div style="background:#1E3A5F;padding:40px;text-align:center;">
                        %s
                        <h1 style="color:white;margin-top:16px;font-size:24px;font-weight:600;">
                            Password Reset Request
                        </h1>
                    </div>

                    <!-- BODY -->
                    <div style="padding:40px;">

                        <p style="font-size:16px;color:#334155;">
                            Hello <strong>%s</strong>,
                        </p>

                        <p style="font-size:15px;color:#475569;line-height:1.7;">
                            We received a request to reset the password for your
                            <strong>%s</strong> account.
                        </p>

                        <p style="font-size:15px;color:#475569;line-height:1.7;margin-top:10px;">
                            Please enter the following verification code in the password reset form:
                        </p>

                        <!-- CODE BOX -->
                        <div style="background:#f8fafc;border:2px dashed #cbd5e1;border-radius:12px;
                                    padding:24px;text-align:center;margin:30px 0;">

                            <p style="margin:0;font-size:12px;color:#64748b;
                                      text-transform:uppercase;letter-spacing:1px;">
                                Your OTP Code
                            </p>

                            <p style="margin:12px 0 0;font-size:28px;
                                      font-weight:700;color:#1E3A5F;
                                      letter-spacing:3px;font-family:monospace;">
                                %s
                            </p>

                            <p style="margin-top:10px;font-size:13px;color:#ef4444;">
                                Expires at %s
                            </p>

                        </div>

                        <p style="font-size:14px;color:#64748b;">
                            Enter this code in the password reset page to create a new password.
                        </p>

                        <p style="font-size:14px;color:#64748b;margin-top:15px;">
                            If you did not request a password reset, please ignore this email.
                        </p>

                    </div>

                    <!-- FOOTER -->
                    <div style="background:#1E3A5F;color:white;padding:20px;text-align:center;">

                        <p style="margin:0;font-size:14px;font-weight:600;">
                            %s
                        </p>

                        <p style="margin-top:6px;font-size:12px;color:rgba(255,255,255,0.6);">
                            Internal HR Management Platform
                        </p>

                        <p style="margin-top:10px;font-size:11px;color:rgba(255,255,255,0.4);">
                            This is an automated message · Please do not reply
                        </p>

                    </div>

                  </div>

                </div>
                """.formatted(
                logoTag,
                userName,
                companyName,
                resetToken,
                expiry,
                companyName
        );
    }
}
