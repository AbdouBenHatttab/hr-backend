package tn.isetbizerte.pfe.hrbackend.infrastructure.email;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import tn.isetbizerte.pfe.hrbackend.infrastructure.email.template.AuthorizationEmailTemplate;
import tn.isetbizerte.pfe.hrbackend.infrastructure.email.template.DocumentEmailTemplate;
import tn.isetbizerte.pfe.hrbackend.infrastructure.email.template.LeaveEmailTemplate;
import tn.isetbizerte.pfe.hrbackend.infrastructure.email.template.LoanEmailTemplate;
import tn.isetbizerte.pfe.hrbackend.infrastructure.email.template.RoleEmailTemplate;
import tn.isetbizerte.pfe.hrbackend.infrastructure.email.template.TaskEmailTemplate;
import tn.isetbizerte.pfe.hrbackend.infrastructure.email.template.TeamEmailTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Email facade for HR workflows.
 * Owns sending, logging, and error handling while template classes build HTML bodies.
 */
@Service
public class HREmailService {

    private static final Logger log = LoggerFactory.getLogger(HREmailService.class);
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String SYSTEM_NAME = "ArabSoft Human Resources Management System";
    private static final String DEPARTMENT  = "Human Resources Department";
    private static final String COMPANY     = "ArabSoft";

    private final JavaMailSender mailSender;
    private final AuthorizationEmailTemplate authorizationEmailTemplate;
    private final DocumentEmailTemplate documentEmailTemplate;
    private final LeaveEmailTemplate leaveEmailTemplate;
    private final LoanEmailTemplate loanEmailTemplate;
    private final RoleEmailTemplate roleEmailTemplate;
    private final TaskEmailTemplate taskEmailTemplate;
    private final TeamEmailTemplate teamEmailTemplate;

    @Value("${spring.mail.username:noreply@arabsoft-hrms.com}")
    private String fromEmail;

    @Value("${app.mail.from-name:ArabSoft Human Resources}")
    private String fromDisplayName;

    @Value("${app.mail.reply-to:}")
    private String replyToEmail;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    public HREmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
        this.authorizationEmailTemplate = new AuthorizationEmailTemplate();
        this.documentEmailTemplate = new DocumentEmailTemplate();
        this.leaveEmailTemplate = new LeaveEmailTemplate();
        this.loanEmailTemplate = new LoanEmailTemplate();
        this.roleEmailTemplate = new RoleEmailTemplate();
        this.taskEmailTemplate = new TaskEmailTemplate();
        this.teamEmailTemplate = new TeamEmailTemplate();
    }

    // 1 - ROLE ASSIGNED (WELCOME)

    @Async
    public void sendRoleAssigned(String email, String firstName, String lastName,
                                  String username, String oldRole, String newRole,
                                  String assignedBy, boolean firstApproval) {
        String name = firstName + " " + lastName;
        String roleLabel = roleEmailTemplate.formatRole(newRole);

        if (firstApproval) {
            send(email,
                "Welcome to the team – Your ArabSoft access is active",
                roleEmailTemplate.buildRoleAssignedBody(name, roleLabel, frontendUrl, fromDisplayName));
            return;
        }

        if ("EMPLOYEE".equals(oldRole) && "TEAM_LEADER".equals(newRole)) {
            send(email,
                "Role Update – You are now a Team Leader",
                roleEmailTemplate.buildPromotionRoleAssignedBody(name, roleLabel, assignedBy, frontendUrl, fromDisplayName));
            return;
        }

        send(email,
            "Role Update – Your ArabSoft access has been updated",
            roleEmailTemplate.buildRoleUpdatedBody(name, roleEmailTemplate.formatRole(oldRole), roleLabel, assignedBy, frontendUrl, fromDisplayName));
    }

    // 2 - LEAVE APPROVED

    @Async
    public void sendLeaveApproved(String email, String firstName, String lastName,
                                   String leaveType, LocalDate startDate, LocalDate endDate,
                                   int days, String referenceId) {
        String name = firstName + " " + lastName;
        send(email,
            "Leave Request Approved – " + referenceId,
            leaveEmailTemplate.buildLeaveApprovedBody(name, leaveType, startDate, endDate, days, referenceId, fromDisplayName));
    }

    // 3 - LEAVE REJECTED

    @Async
    public void sendLeaveRejected(String email, String firstName, String lastName,
                                   String leaveType, LocalDate startDate, LocalDate endDate,
                                   String reason, String referenceId) {
        String name = firstName + " " + lastName;
        send(email,
            "Leave Request Not Approved – " + referenceId,
            leaveEmailTemplate.buildLeaveRejectedBody(name, leaveType, startDate, endDate, reason, referenceId, fromDisplayName));
    }

    // 4 - LOAN APPROVED

    public boolean sendLoanApproved(String email, String firstName, String lastName,
                                    double amount, int months, double monthlyInstallment,
                                    String referenceId) {
        String requestId = extractRequestId(referenceId);
        String subject = "Loan Request Approved – " + referenceId;
        log.info("HREmailService.sendLoanApproved called: recipientEmail={} requestId={} subject={}",
                email, requestId, subject);
        try {
            String name = firstName + " " + lastName;
            boolean result = send(email,
                subject,
                loanEmailTemplate.buildLoanApprovedBody(name, amount, months, monthlyInstallment, referenceId, fromDisplayName));
            log.info("HREmailService.sendLoanApproved result: recipientEmail={} requestId={} subject={} result={}",
                    email, requestId, subject, result);
            return result;
        } catch (Exception e) {
            log.error("HREmailService.sendLoanApproved exception: recipientEmail={} requestId={} subject={} message={}",
                    email, requestId, subject, e.getMessage(), e);
            return false;
        }
    }

    // 5 - LOAN REJECTED

    public boolean sendLoanRejected(String email, String firstName, String lastName,
                                    double amount, String reason, String referenceId) {
        String requestId = extractRequestId(referenceId);
        String subject = "Loan Request Not Approved – " + referenceId;
        log.info("HREmailService.sendLoanRejected called: recipientEmail={} requestId={} subject={}",
                email, requestId, subject);
        try {
            String name = firstName + " " + lastName;
            boolean result = send(email,
                subject,
                loanEmailTemplate.buildLoanRejectedBody(name, amount, reason, referenceId, fromDisplayName));
            log.info("HREmailService.sendLoanRejected result: recipientEmail={} requestId={} subject={} result={}",
                    email, requestId, subject, result);
            return result;
        } catch (Exception e) {
            log.error("HREmailService.sendLoanRejected exception: recipientEmail={} requestId={} subject={} message={}",
                    email, requestId, subject, e.getMessage(), e);
            return false;
        }
    }

    public boolean sendLoanMeetingNotification(String email, String firstName, String lastName,
                                               LocalDateTime meetingAt, boolean updated,
                                               String referenceId) {
        String requestId = extractRequestId(referenceId);
        String subject = updated
                ? "Loan Meeting Updated – " + referenceId
                : "Loan Meeting Scheduled – " + referenceId;
        log.info("HREmailService.sendLoanMeetingNotification called: recipientEmail={} requestId={} subject={} updated={}",
                email, requestId, subject, updated);
        try {
            String name = firstName + " " + lastName;
            boolean result = send(email,
                    subject,
                    loanEmailTemplate.buildLoanMeetingNotificationBody(name, formatDateTimeOrDash(meetingAt), updated, referenceId, frontendUrl, fromDisplayName));
            log.info("HREmailService.sendLoanMeetingNotification result: recipientEmail={} requestId={} subject={} result={}",
                    email, requestId, subject, result);
            return result;
        } catch (Exception e) {
            log.error("HREmailService.sendLoanMeetingNotification exception: recipientEmail={} requestId={} subject={} message={}",
                    email, requestId, subject, e.getMessage(), e);
            return false;
        }
    }

    public boolean sendLoanFinalFileReady(String email, String firstName, String lastName,
                                          String referenceId) {
        String requestId = extractRequestId(referenceId);
        String subject = "Final Loan Document Ready – " + referenceId;
        log.info("HREmailService.sendLoanFinalFileReady called: recipientEmail={} requestId={} subject={}",
                email, requestId, subject);
        try {
            String name = firstName + " " + lastName;
            boolean result = send(email,
                    subject,
                    loanEmailTemplate.buildLoanFinalFileReadyBody(name, referenceId, frontendUrl, fromDisplayName));
            log.info("HREmailService.sendLoanFinalFileReady result: recipientEmail={} requestId={} subject={} result={}",
                    email, requestId, subject, result);
            return result;
        } catch (Exception e) {
            log.error("HREmailService.sendLoanFinalFileReady exception: recipientEmail={} requestId={} subject={} message={}",
                    email, requestId, subject, e.getMessage(), e);
            return false;
        }
    }

    // 6 - PASSWORD RESET OTP

    @Async
    public void sendPasswordReset(String email, String firstName, String lastName,
                                   String otpCode, String expiresAt) {
        String name = firstName + " " + lastName;
        send(email,
            "Password Reset Request – " + COMPANY + " HRMS",
            buildPasswordResetBody(name, otpCode, expiresAt));
    }

    @Async
    public void sendPasswordChangeOtp(String email, String firstName, String lastName,
                                      String otpCode, String expiresAt) {
        String name = firstName + " " + lastName;
        send(email,
                "Password Change Code – " + COMPANY + " HRMS",
                buildPasswordChangeBody(name, otpCode, expiresAt));
    }

    // 7 - TASK ASSIGNED

    @Async
    public void sendTaskAssigned(String email, String firstName, String lastName,
                                 String taskTitle, String taskDescription, String projectName,
                                 String priority, LocalDate startDate, LocalDate dueDate, String assignedBy) {
        String name = firstName + " " + lastName;
        send(email,
            "New Task Assigned – " + COMPANY + " HRMS",
            taskEmailTemplate.buildTaskAssignedBody(name, taskTitle, taskDescription, projectName, priority, startDate, dueDate, assignedBy, frontendUrl, fromDisplayName));
    }

    @Async
    public void sendTeamAssigned(String email, String firstName, String lastName, String teamName) {
        String name = firstName + " " + lastName;
        send(email,
                "Team Assignment Updated - " + COMPANY + " HRMS",
                teamEmailTemplate.buildTeamAssignedBody(name, teamName, frontendUrl, fromDisplayName));
    }

    @Async
    public void sendTeamChanged(String email, String firstName, String lastName,
                                String oldTeamName, String newTeamName) {
        String name = firstName + " " + lastName;
        send(email,
                "Team Assignment Changed - " + COMPANY + " HRMS",
                teamEmailTemplate.buildTeamChangedBody(name, oldTeamName, newTeamName, frontendUrl, fromDisplayName));
    }

    @Async
    public void sendTeamRemoved(String email, String firstName, String lastName, String oldTeamName) {
        String name = firstName + " " + lastName;
        send(email,
                "Team Assignment Removed - " + COMPANY + " HRMS",
                teamEmailTemplate.buildTeamRemovedBody(name, oldTeamName, frontendUrl, fromDisplayName));
    }

    @Async
    public void sendTeamLeaderAssigned(String email, String firstName, String lastName, String teamName) {
        String name = firstName + " " + lastName;
        send(email,
                "Team Leader Assignment - " + COMPANY + " HRMS",
                teamEmailTemplate.buildTeamLeaderAssignedBody(name, teamName, frontendUrl, fromDisplayName));
    }

    @Async
    public void sendTeamLeaderRemoved(String email, String firstName, String lastName, String teamName) {
        String name = firstName + " " + lastName;
        send(email,
                "Team Leader Assignment Updated - " + COMPANY + " HRMS",
                teamEmailTemplate.buildTeamLeaderRemovedBody(name, teamName, fromDisplayName));
    }

    // 8 - TASK UPDATED

    @Async
    public void sendTaskUpdated(String email, String firstName, String lastName,
                                String taskTitle, String taskDescription, String projectName,
                                String priority, LocalDate startDate, LocalDate dueDate, String updatedBy) {
        String name = firstName + " " + lastName;
        send(email,
                "Task Updated – " + COMPANY + " HRMS",
                taskEmailTemplate.buildTaskUpdatedBody(name, taskTitle, taskDescription, projectName, priority, startDate, dueDate, updatedBy, frontendUrl, fromDisplayName));
    }

    // 9 - TASK COMPLETED (TEAM LEADER)

    @Async
    public void sendTaskCompletedToLeader(String email, String firstName, String lastName,
                                          String taskTitle, String projectName, String completedBy) {
        String name = firstName + " " + lastName;
        send(email,
                "Task Completed – " + COMPANY + " HRMS",
                taskEmailTemplate.buildTaskCompletedBody(name, taskTitle, projectName, completedBy, frontendUrl, fromDisplayName));
    }

    // DOCUMENT READY

    public boolean sendDocumentReady(String email, String firstName, String lastName,
                                     String referenceId) {
        String requestId = extractRequestId(referenceId);
        String subject = "Document Ready – " + referenceId;
        log.info("HREmailService.sendDocumentReady called: recipientEmail={} requestId={} subject={}",
                email, requestId, subject);
        try {
            String name = firstName + " " + lastName;
            boolean result = send(email,
                    subject,
                    documentEmailTemplate.buildDocumentReadyBody(name, referenceId, frontendUrl, fromDisplayName));
            log.info("HREmailService.sendDocumentReady result: recipientEmail={} requestId={} subject={} result={}",
                    email, requestId, subject, result);
            return result;
        } catch (Exception e) {
            log.error("HREmailService.sendDocumentReady exception: recipientEmail={} requestId={} subject={} message={}",
                    email, requestId, subject, e.getMessage(), e);
            return false;
        }
    }

    public boolean sendRequiredDocumentUploaded(String email, String firstName, String lastName,
                                                String documentLabel, String fileName, boolean replaced,
                                                String referenceId) {
        String subject = replaced
                ? "Required HR Document Updated - " + referenceId
                : "Required HR Document Added - " + referenceId;
        log.info("HREmailService.sendRequiredDocumentUploaded called: recipientEmail={} referenceId={} subject={} replaced={}",
                email, referenceId, subject, replaced);
        try {
            String name = firstName + " " + lastName;
            boolean result = send(email,
                    subject,
                    documentEmailTemplate.buildRequiredDocumentUploadedBody(name, documentLabel, fileName, replaced, frontendUrl, fromDisplayName));
            log.info("HREmailService.sendRequiredDocumentUploaded result: recipientEmail={} referenceId={} subject={} result={}",
                    email, referenceId, subject, result);
            return result;
        } catch (Exception e) {
            log.error("HREmailService.sendRequiredDocumentUploaded exception: recipientEmail={} referenceId={} subject={} message={}",
                    email, referenceId, subject, e.getMessage(), e);
            return false;
        }
    }

    // AUTHORIZATION DECISION

    public boolean sendAuthorizationDecision(String email, String firstName, String lastName,
                                             String authorizationType, LocalDate startDate,
                                             LocalDate endDate, LocalDate absenceDate,
                                             LocalTime fromTime, LocalTime toTime,
                                             String equipmentType,
                                             LocalDateTime processedAt,
                                             boolean approved, String decisionNote,
                                             String referenceId) {
        String requestId = extractRequestId(referenceId);
        String subject = approved
                ? "Authorization Request Approved"
                : "Authorization Request Rejected";
        log.info("HREmailService.sendAuthorizationDecision called: recipientEmail={} requestId={} subject={} approved={}",
                email, requestId, subject, approved);
        try {
            String name = firstName + " " + lastName;
            boolean result = send(email,
                    subject,
                    authorizationEmailTemplate.buildAuthorizationDecisionBody(name, authorizationType, startDate, endDate,
                            absenceDate, fromTime, toTime,
                            equipmentType, formatDateTimeOrDash(processedAt), approved, decisionNote,
                            referenceId, frontendUrl, fromDisplayName));
            log.info("HREmailService.sendAuthorizationDecision result: recipientEmail={} requestId={} subject={} result={}",
                    email, requestId, subject, result);
            return result;
        } catch (Exception e) {
            log.error("HREmailService.sendAuthorizationDecision exception: recipientEmail={} requestId={} subject={} message={}",
                    email, requestId, subject, e.getMessage(), e);
            return false;
        }
    }

    // INTERNAL SEND

    private boolean send(String to, String subject, String html) {
        try {
            log.info("Sending email via JavaMailSender: recipientEmail={} subject={}", to, subject);
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(fromEmail, fromDisplayName);
            if (replyToEmail != null && !replyToEmail.isBlank()) {
                helper.setReplyTo(replyToEmail);
            }
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);

            ClassPathResource logo = new ClassPathResource("static/images/logo.jpg");
            if (logo.exists()) helper.addInline("logo", logo);

            mailSender.send(msg);
            log.info("Email sent to {} — {}", to, subject);
            return true;
        } catch (Exception e) {
            log.error("Failed to send email: recipientEmail={} subject={} errorType={} message={}",
                    to, subject, e.getClass().getSimpleName(), e.getMessage(), e);
            return false;
        }
    }

    // HTML BUILDERS

    private String buildPasswordResetBody(String name, String otpCode, String expiresAt) {
        return wrap("Password Reset Request", "Keep this code private", """
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                Dear <strong>%s</strong>,
            </p>
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                We received a request to reset the password for your
                <strong>ArabSoft HRMS</strong> account.
                Please use the verification code below:
            </p>
            <div style="background:#F0F9FF;border:2px dashed #0EA5E9;border-radius:12px;
                        padding:28px;text-align:center;margin:28px 0;">
                <p style="margin:0;font-size:12px;color:#0369A1;font-weight:600;
                           text-transform:uppercase;letter-spacing:0.1em;">
                    Your One-Time Password (OTP)
                </p>
                <p style="margin:12px 0 0;font-size:36px;font-weight:700;
                           color:#0C4A6E;letter-spacing:8px;font-family:monospace;">
                    %s
                </p>
                <p style="margin:12px 0 0;font-size:13px;color:#DC2626;font-weight:600;">
                    Expires at: %s
                </p>
            </div>
            <p style="font-size:14px;color:#6B7280;line-height:1.8;">
                Enter this code in the password reset page to set a new password.
                The code is valid for a limited time only.
            </p>
            <div style="background:#FFFBEB;border-left:4px solid #F59E0B;border-radius:8px;
                        padding:14px 18px;margin-top:20px;">
                <p style="margin:0;font-size:14px;color:#92400E;">
                    If you did not request this password reset, please ignore this email.
                    Your account remains secure.
                </p>
            </div>
            """.formatted(name, otpCode, expiresAt));
    }

    private String buildPasswordChangeBody(String name, String otpCode, String expiresAt) {
        return wrap("Password Change Verification", "Keep this code private", """
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                Dear <strong>%s</strong>,
            </p>
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                Use the following OTP code to confirm changing your account password.
            </p>
            <div style="background:#F0F9FF;border:2px dashed #0EA5E9;border-radius:12px;
                        padding:28px;text-align:center;margin:28px 0;">
                <p style="margin:0;font-size:12px;color:#0369A1;font-weight:600;
                           text-transform:uppercase;letter-spacing:0.1em;">
                    Your One-Time Password (OTP)
                </p>
                <p style="margin:12px 0 0;font-size:36px;font-weight:700;
                           color:#0C4A6E;letter-spacing:8px;font-family:monospace;">
                    %s
                </p>
                <p style="margin:12px 0 0;font-size:13px;color:#DC2626;font-weight:600;">
                    Expires at: %s
                </p>
            </div>
            <p style="font-size:14px;color:#6B7280;line-height:1.8;">
                Enter this code in your profile to set a new password.
                The code is valid for a limited time only.
            </p>
            <div style="background:#FFFBEB;border-left:4px solid #F59E0B;border-radius:8px;
                        padding:14px 18px;margin-top:20px;">
                <p style="margin:0;font-size:14px;color:#92400E;">
                    If you did not request this password change, please contact HR immediately.
                </p>
            </div>
            """.formatted(name, otpCode, expiresAt));
    }

    // SHARED LAYOUT COMPONENTS

    /** Wraps content in the ArabSoft HRMS email shell */
    private String wrap(String title, String subtitle, String content) {
        boolean hasLogo = new ClassPathResource("static/images/logo.jpg").exists();
        String logoTag = hasLogo
            ? "<img src='cid:logo' width='60' style='display:block;'/>"
            : "";
        return """
            <!DOCTYPE html>
            <html>
            <body style="margin:0;padding:0;background:#F3F4F6;
                         font-family:'Segoe UI',Arial,sans-serif;">
            <table width="100%%" cellpadding="0" cellspacing="0">
            <tr><td align="center" style="padding:40px 16px;">

              <table width="600" cellpadding="0" cellspacing="0"
                     style="background:white;border-radius:16px;
                            overflow:hidden;box-shadow:0 8px 32px rgba(0,0,0,0.08);">

                <!-- HEADER -->
                <tr>
                  <td style="background:#0F172A;padding:32px 40px;">
                    <table width="100%%" cellpadding="0" cellspacing="0">
                    <tr>
                      <td>%s</td>
                      <td style="padding-left:16px;vertical-align:middle;">
                        <p style="margin:0;font-size:11px;color:#64748B;
                                   text-transform:uppercase;letter-spacing:0.1em;">
                          %s
                        </p>
                      </td>
                    </tr>
                    </table>
                    <div style="border-top:1px solid #1E293B;margin:20px 0;"></div>
                    <p style="margin:0;font-size:22px;font-weight:700;color:#FFFFFF;">%s</p>
                    <p style="margin:6px 0 0;font-size:14px;color:#94A3B8;">%s</p>
                  </td>
                </tr>

                <!-- BODY -->
                <tr>
                  <td style="padding:36px 40px;">
                    %s
                  </td>
                </tr>

                <!-- FOOTER -->
                <tr>
                  <td style="background:#F8FAFC;border-top:1px solid #E2E8F0;
                             padding:24px 40px;">
                    <p style="margin:0;font-size:13px;font-weight:700;color:#0F172A;">
                      %s
                    </p>
                    <p style="margin:4px 0 0;font-size:12px;color:#64748B;">
                      %s
                    </p>
                    <p style="margin:12px 0 0;font-size:11px;color:#94A3B8;">
                      This is an automated message generated by ArabSoft HRMS.
                      Please do not reply to this email.
                    </p>
                  </td>
                </tr>

              </table>
            </td></tr>
            </table>
            </body>
            </html>
            """.formatted(logoTag, SYSTEM_NAME, title, subtitle, content, fromDisplayName, SYSTEM_NAME);
    }

    /** Renders a grid of key-value info pairs */
    private String infoGrid(String[][] rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" ")
          .append("style=\"border:1px solid #E5E7EB;border-radius:10px;overflow:hidden;margin:20px 0;\">");
        for (int i = 0; i < rows.length; i++) {
            String bg = i % 2 == 0 ? "#F9FAFB" : "#FFFFFF";
            sb.append("<tr style=\"background:").append(bg).append(";\">")
              .append("<td style=\"padding:12px 16px;font-size:13px;color:#6B7280;")
              .append("font-weight:600;width:40%;border-bottom:1px solid #F3F4F6;\">")
              .append(rows[i][0]).append("</td>")
              .append("<td style=\"padding:12px 16px;font-size:14px;color:#111827;")
              .append("font-weight:600;border-bottom:1px solid #F3F4F6;\">")
              .append(rows[i][1]).append("</td></tr>");
        }
        sb.append("</table>");
        return sb.toString();
    }

    /** Renders a CTA button */
    private String actionButton(String label, String url, String color) {
        return "<div style=\"text-align:center;margin-top:28px;\">" +
               "<a href=\"" + url + "\" style=\"background:" + color + ";color:white;" +
               "text-decoration:none;padding:14px 32px;border-radius:8px;" +
               "font-size:15px;font-weight:600;display:inline-block;\">" +
               label + "</a></div>";
    }

    private String formatDateTimeOrDash(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DATETIME_FMT) : "—";
    }

    private String extractRequestId(String referenceId) {
        if (referenceId == null || referenceId.isBlank()) return "unknown";
        int separator = referenceId.lastIndexOf('-');
        if (separator >= 0 && separator < referenceId.length() - 1) {
            return referenceId.substring(separator + 1);
        }
        return referenceId;
    }
}
