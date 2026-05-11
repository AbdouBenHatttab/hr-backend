package tn.isetbizerte.pfe.hrbackend.modules.hr.service;

import jakarta.mail.internet.MimeMessage;
import jakarta.mail.MessagingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import tn.isetbizerte.pfe.hrbackend.common.enums.TypeRole;
import tn.isetbizerte.pfe.hrbackend.common.exception.BadRequestException;
import tn.isetbizerte.pfe.hrbackend.common.exception.ResourceNotFoundException;
import tn.isetbizerte.pfe.hrbackend.common.exception.UnauthorizedException;
import tn.isetbizerte.pfe.hrbackend.modules.hr.dto.SendHrManualEmailRequest;
import tn.isetbizerte.pfe.hrbackend.modules.hr.dto.SendHrManualEmailResponse;
import tn.isetbizerte.pfe.hrbackend.modules.hr.entity.HrManualEmailLog;
import tn.isetbizerte.pfe.hrbackend.modules.hr.entity.HrManualEmailStatus;
import tn.isetbizerte.pfe.hrbackend.modules.hr.repository.HrManualEmailLogRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.Person;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.UserRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.service.AuthenticatedUserResolver;

import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;

@Service
public class HrManualEmailService {

    private static final Logger log = LoggerFactory.getLogger(HrManualEmailService.class);
    private static final int BODY_PREVIEW_LIMIT = 500;

    private final JavaMailSender mailSender;
    private final UserRepository userRepository;
    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final HrManualEmailLogRepository logRepository;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.mail.from-name:ArabSoft Human Resources}")
    private String fromDisplayName;

    @Value("${app.mail.reply-to:}")
    private String replyToEmail;

    public HrManualEmailService(JavaMailSender mailSender,
                                UserRepository userRepository,
                                AuthenticatedUserResolver authenticatedUserResolver,
                                HrManualEmailLogRepository logRepository) {
        this.mailSender = mailSender;
        this.userRepository = userRepository;
        this.authenticatedUserResolver = authenticatedUserResolver;
        this.logRepository = logRepository;
    }

    public SendHrManualEmailResponse sendManualEmail(SendHrManualEmailRequest request, Jwt jwt) {
        User sender = authenticatedUserResolver.require(jwt);
        if (sender.getRole() != TypeRole.HR_MANAGER) {
            throw new UnauthorizedException("Only HR_MANAGER can send manual HR emails.");
        }
        User recipient = userRepository.findById(request.getRecipientUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Person recipientPerson = recipient.getPerson();
        if (recipientPerson == null || recipientPerson.getEmail() == null || recipientPerson.getEmail().isBlank()) {
            throw new BadRequestException("Recipient user does not have an email address.");
        }

        String recipientEmail = recipientPerson.getEmail().trim();
        String senderDisplay = resolveDisplayName(sender);
        String senderUsername = sender.getUsername();
        String subject = request.getSubject().trim();
        String message = request.getMessage().trim();
        String bodyPreview = buildBodyPreview(message);

        HrManualEmailLog logEntry = new HrManualEmailLog();
        logEntry.setRecipientUserId(recipient.getId());
        logEntry.setRecipientEmail(recipientEmail);
        logEntry.setSentByUserId(sender.getId());
        logEntry.setSentByUsername(senderUsername);
        logEntry.setSentByDisplayName(senderDisplay);
        logEntry.setSubject(subject);
        logEntry.setBodyPreview(bodyPreview);
        logEntry.setReferenceType(normalizeReferenceType(request.getReferenceType()));
        logEntry.setReferenceId(request.getReferenceId());
        logEntry.setCreatedAt(LocalDateTime.now());

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(fromEmail, fromDisplayName);
            if (replyToEmail != null && !replyToEmail.isBlank()) {
                helper.setReplyTo(replyToEmail);
            }
            helper.setTo(recipientEmail);
            helper.setSubject(subject);
            helper.setText(buildHtmlBody(senderDisplay, recipientPerson, subject, message, request.getReferenceType(), request.getReferenceId()), true);

            mailSender.send(mimeMessage);

            logEntry.setStatus(HrManualEmailStatus.SENT);
            logEntry.setSentAt(LocalDateTime.now());
            HrManualEmailLog saved = logRepository.save(logEntry);

            log.info("Manual HR email sent senderUserId={} senderUsername={} recipientUserId={} recipientEmail={} subject={} referenceType={} referenceId={}",
                    sender.getId(), senderUsername, recipient.getId(), recipientEmail, subject, request.getReferenceType(), request.getReferenceId());
            return toResponse(saved, "Manual email sent successfully");
        } catch (MailException | MessagingException | UnsupportedEncodingException e) {
            logEntry.setStatus(HrManualEmailStatus.FAILED);
            logEntry.setErrorMessage(safeErrorMessage(e));
            HrManualEmailLog saved = logRepository.save(logEntry);

            log.warn("Manual HR email failed senderUserId={} senderUsername={} recipientUserId={} recipientEmail={} subject={} referenceType={} referenceId={} error={}",
                    sender.getId(), senderUsername, recipient.getId(), recipientEmail, subject, request.getReferenceType(), request.getReferenceId(), e.getClass().getSimpleName());
            throw new IllegalStateException("Failed to send manual HR email", e);
        }
    }

    private SendHrManualEmailResponse toResponse(HrManualEmailLog saved, String message) {
        SendHrManualEmailResponse response = new SendHrManualEmailResponse();
        response.setLogId(saved.getId());
        response.setStatus(saved.getStatus().name());
        response.setMessage(message);
        response.setRecipientUserId(saved.getRecipientUserId());
        response.setRecipientEmail(saved.getRecipientEmail());
        response.setSentByUserId(saved.getSentByUserId());
        response.setSentByUsername(saved.getSentByUsername());
        response.setSentAt(saved.getSentAt());
        return response;
    }

    private String buildHtmlBody(String senderDisplay, Person recipientPerson, String subject, String message,
                                 String referenceType, Long referenceId) {
        String recipientName = resolvePersonName(recipientPerson);
        String safeRecipientName = HtmlUtils.htmlEscape(recipientName);
        String safeSubject = HtmlUtils.htmlEscape(subject);
        String safeMessage = HtmlUtils.htmlEscape(message)
                .replace("\r\n", "\n")
                .replace("\n", "<br/>");
        String safeReference = HtmlUtils.htmlEscape(buildReferenceLine(referenceType, referenceId));
        return """
                <!DOCTYPE html>
                <html>
                <body style="margin:0;padding:0;background:#F3F4F6;font-family:'Segoe UI',Arial,sans-serif;">
                <table width="100%%" cellpadding="0" cellspacing="0">
                <tr><td align="center" style="padding:40px 16px;">
                  <table width="600" cellpadding="0" cellspacing="0"
                         style="background:white;border-radius:16px;overflow:hidden;box-shadow:0 8px 32px rgba(0,0,0,0.08);">
                    <tr>
                      <td style="background:#0F172A;padding:32px 40px;">
                        <p style="margin:0;font-size:11px;color:#64748B;text-transform:uppercase;letter-spacing:0.1em;">
                          ArabSoft Human Resources
                        </p>
                        <div style="border-top:1px solid #1E293B;margin:20px 0;"></div>
                        <p style="margin:0;font-size:22px;font-weight:700;color:#FFFFFF;">%s</p>
                        <p style="margin:6px 0 0;font-size:14px;color:#94A3B8;">Manual HR message</p>
                      </td>
                    </tr>
                    <tr>
                      <td style="padding:36px 40px;">
                        <p style="font-size:15px;color:#374151;line-height:1.8;">
                          Dear <strong>%s</strong>,
                        </p>
                        <div style="font-size:15px;color:#374151;line-height:1.8;">
                          %s
                        </div>
                        %s
                        <div style="margin-top:28px;padding-top:18px;border-top:1px solid #E5E7EB;">
                          <p style="margin:0;font-size:13px;color:#6B7280;">
                            %s
                          </p>
                        </div>
                      </td>
                    </tr>
                    <tr>
                      <td style="background:#F8FAFC;border-top:1px solid #E2E8F0;padding:24px 40px;">
                        <p style="margin:0;font-size:13px;font-weight:700;color:#0F172A;">%s</p>
                        <p style="margin:4px 0 0;font-size:12px;color:#64748B;">Human Resources</p>
                      </td>
                    </tr>
                  </table>
                </td></tr>
                </table>
                </body>
                </html>
                """.formatted(
                safeSubject,
                safeRecipientName,
                safeMessage,
                safeReference.isBlank() ? "" : "<p style=\"margin-top:18px;font-size:13px;color:#6B7280;\">"
                        + safeReference + "</p>",
                HtmlUtils.htmlEscape("Sent by " + senderDisplay),
                HtmlUtils.htmlEscape(fromDisplayName)
        );
    }

    private String buildReferenceLine(String referenceType, Long referenceId) {
        String normalizedType = normalizeReferenceType(referenceType);
        if ((normalizedType == null || normalizedType.isBlank()) && referenceId == null) {
            return "";
        }
        if (normalizedType != null && !normalizedType.isBlank() && referenceId != null) {
            return "Reference: " + normalizedType + " #" + referenceId;
        }
        if (normalizedType != null && !normalizedType.isBlank()) {
            return "Reference: " + normalizedType;
        }
        return "Reference: #" + referenceId;
    }

    private String normalizeReferenceType(String referenceType) {
        if (referenceType == null) {
            return null;
        }
        String trimmed = referenceType.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String resolveDisplayName(User user) {
        if (user == null || user.getPerson() == null) {
            return user != null && user.getUsername() != null ? user.getUsername() : "ArabSoft";
        }
        Person person = user.getPerson();
        String firstName = person.getFirstName() == null ? "" : person.getFirstName().trim();
        String lastName = person.getLastName() == null ? "" : person.getLastName().trim();
        String fullName = (firstName + " " + lastName).trim();
        if (!fullName.isBlank()) {
            return fullName;
        }
        return user.getUsername() != null ? user.getUsername() : "ArabSoft";
    }

    private String resolvePersonName(Person person) {
        if (person == null) {
            return "Employee";
        }
        String firstName = person.getFirstName() == null ? "" : person.getFirstName().trim();
        String lastName = person.getLastName() == null ? "" : person.getLastName().trim();
        String fullName = (firstName + " " + lastName).trim();
        if (!fullName.isBlank()) {
            return fullName;
        }
        return "Employee";
    }

    private String buildBodyPreview(String message) {
        String normalized = message.replace("\r\n", "\n").trim().replaceAll("\\s+", " ");
        if (normalized.length() <= BODY_PREVIEW_LIMIT) {
            return normalized;
        }
        return normalized.substring(0, BODY_PREVIEW_LIMIT - 3) + "...";
    }

    private String safeErrorMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return e.getClass().getSimpleName();
        }
        String normalized = message.trim().replaceAll("\\s+", " ");
        if (normalized.length() > 500) {
            normalized = normalized.substring(0, 497) + "...";
        }
        return e.getClass().getSimpleName() + ": " + normalized;
    }
}
