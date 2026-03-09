package tn.isetbizerte.pfe.hrbackend.debug;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * DEBUG: Direct email test - no Kafka, no Keycloak, just email.
 * Call: GET http://localhost:8081/public/test-email?to=your@email.com
 */
@RestController
public class EmailTestController {

    private static final Logger logger = LoggerFactory.getLogger(EmailTestController.class);

    private final JavaMailSender mailSender;

    public EmailTestController(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @GetMapping("/public/test-email")
    public Map<String, Object> testEmail(@RequestParam String to) {
        Map<String, Object> response = new HashMap<>();
        logger.info("🧪 TEST: Sending test email to: {}", to);

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("arabsoft.info@gmail.com");
            message.setTo(to);
            message.setSubject("HR Backend - Email Test");
            message.setText("If you receive this email, the SMTP configuration is working correctly!\n\nSent at: " + java.time.LocalDateTime.now());

            mailSender.send(message);

            response.put("success", true);
            response.put("message", "Email sent successfully to: " + to);
            logger.info("✅ TEST: Email sent successfully to: {}", to);

        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("errorClass", e.getClass().getSimpleName());
            logger.error("❌ TEST: Email FAILED to {}: {}", to, e.getMessage(), e);
        }

        return response;
    }
}

