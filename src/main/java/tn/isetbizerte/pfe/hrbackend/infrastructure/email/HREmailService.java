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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * ArabSoft Human Resources Management System (HRMS)
 * Central email service — handles all outgoing emails.
 *
 * Emails:
 *  1. Role assigned (welcome)
 *  2. Leave approved
 *  3. Leave rejected
 *  4. Loan approved
 *  5. Loan rejected
 *  6. Password reset OTP
 *  7. Task assigned
 */
@Service
public class HREmailService {

    private static final Logger log = LoggerFactory.getLogger(HREmailService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String SYSTEM_NAME = "ArabSoft Human Resources Management System";
    private static final String DEPARTMENT  = "Human Resources Department";
    private static final String COMPANY     = "ArabSoft";

    private final JavaMailSender mailSender;

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
    }

    // ═══════════════════════════════════════════════════════════════════
    // 1 — ROLE ASSIGNED (WELCOME)
    // ═══════════════════════════════════════════════════════════════════

    @Async
    public void sendRoleAssigned(String email, String firstName, String lastName,
                                  String username, String oldRole, String newRole,
                                  String assignedBy, boolean firstApproval) {
        String name = firstName + " " + lastName;
        String roleLabel = formatRole(newRole);

        if (firstApproval) {
            send(email,
                "Welcome to the team – Your ArabSoft access is active",
                buildRoleAssignedBody(name, roleLabel));
            return;
        }

        if ("EMPLOYEE".equals(oldRole) && "TEAM_LEADER".equals(newRole)) {
            send(email,
                "Role Update – You are now a Team Leader",
                buildPromotionRoleAssignedBody(name, roleLabel, assignedBy));
            return;
        }

        send(email,
            "Role Update – Your ArabSoft access has been updated",
            buildRoleUpdatedBody(name, formatRole(oldRole), roleLabel, assignedBy));
    }

    // ═══════════════════════════════════════════════════════════════════
    // 2 — LEAVE APPROVED
    // ═══════════════════════════════════════════════════════════════════

    @Async
    public void sendLeaveApproved(String email, String firstName, String lastName,
                                   String leaveType, LocalDate startDate, LocalDate endDate,
                                   int days, String referenceId) {
        String name = firstName + " " + lastName;
        send(email,
            "Leave Request Approved – " + referenceId,
            buildLeaveApprovedBody(name, leaveType, startDate, endDate, days, referenceId));
    }

    // ═══════════════════════════════════════════════════════════════════
    // 3 — LEAVE REJECTED
    // ═══════════════════════════════════════════════════════════════════

    @Async
    public void sendLeaveRejected(String email, String firstName, String lastName,
                                   String leaveType, LocalDate startDate, LocalDate endDate,
                                   String reason, String referenceId) {
        String name = firstName + " " + lastName;
        send(email,
            "Leave Request Not Approved – " + referenceId,
            buildLeaveRejectedBody(name, leaveType, startDate, endDate, reason, referenceId));
    }

    // ═══════════════════════════════════════════════════════════════════
    // 4 — LOAN APPROVED
    // ═══════════════════════════════════════════════════════════════════

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
                buildLoanApprovedBody(name, amount, months, monthlyInstallment, referenceId));
            log.info("HREmailService.sendLoanApproved result: recipientEmail={} requestId={} subject={} result={}",
                    email, requestId, subject, result);
            return result;
        } catch (Exception e) {
            log.error("HREmailService.sendLoanApproved exception: recipientEmail={} requestId={} subject={} message={}",
                    email, requestId, subject, e.getMessage(), e);
            return false;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 5 — LOAN REJECTED
    // ═══════════════════════════════════════════════════════════════════

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
                buildLoanRejectedBody(name, amount, reason, referenceId));
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
                    buildLoanMeetingNotificationBody(name, meetingAt, updated, referenceId));
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
                    buildLoanFinalFileReadyBody(name, referenceId));
            log.info("HREmailService.sendLoanFinalFileReady result: recipientEmail={} requestId={} subject={} result={}",
                    email, requestId, subject, result);
            return result;
        } catch (Exception e) {
            log.error("HREmailService.sendLoanFinalFileReady exception: recipientEmail={} requestId={} subject={} message={}",
                    email, requestId, subject, e.getMessage(), e);
            return false;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 6 — PASSWORD RESET OTP
    // ═══════════════════════════════════════════════════════════════════

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

    // ═══════════════════════════════════════════════════════════════════
    // 7 — TASK ASSIGNED
    // ═══════════════════════════════════════════════════════════════════

    @Async
    public void sendTaskAssigned(String email, String firstName, String lastName,
                                 String taskTitle, String taskDescription, String projectName,
                                 String priority, LocalDate startDate, LocalDate dueDate, String assignedBy) {
        String name = firstName + " " + lastName;
        send(email,
            "New Task Assigned – " + COMPANY + " HRMS",
            buildTaskAssignedBody(name, taskTitle, taskDescription, projectName, priority, startDate, dueDate, assignedBy));
    }

    @Async
    public void sendTeamAssigned(String email, String firstName, String lastName, String teamName) {
        String name = firstName + " " + lastName;
        send(email,
                "Team Assignment Updated - " + COMPANY + " HRMS",
                buildTeamAssignedBody(name, teamName));
    }

    @Async
    public void sendTeamChanged(String email, String firstName, String lastName,
                                String oldTeamName, String newTeamName) {
        String name = firstName + " " + lastName;
        send(email,
                "Team Assignment Changed - " + COMPANY + " HRMS",
                buildTeamChangedBody(name, oldTeamName, newTeamName));
    }

    @Async
    public void sendTeamRemoved(String email, String firstName, String lastName, String oldTeamName) {
        String name = firstName + " " + lastName;
        send(email,
                "Team Assignment Removed - " + COMPANY + " HRMS",
                buildTeamRemovedBody(name, oldTeamName));
    }

    @Async
    public void sendTeamLeaderAssigned(String email, String firstName, String lastName, String teamName) {
        String name = firstName + " " + lastName;
        send(email,
                "Team Leader Assignment - " + COMPANY + " HRMS",
                buildTeamLeaderAssignedBody(name, teamName));
    }

    @Async
    public void sendTeamLeaderRemoved(String email, String firstName, String lastName, String teamName) {
        String name = firstName + " " + lastName;
        send(email,
                "Team Leader Assignment Updated - " + COMPANY + " HRMS",
                buildTeamLeaderRemovedBody(name, teamName));
    }

    // ═══════════════════════════════════════════════════════════════════
    // 8 — TASK UPDATED
    // ═══════════════════════════════════════════════════════════════════

    @Async
    public void sendTaskUpdated(String email, String firstName, String lastName,
                                String taskTitle, String taskDescription, String projectName,
                                String priority, LocalDate startDate, LocalDate dueDate, String updatedBy) {
        String name = firstName + " " + lastName;
        send(email,
                "Task Updated – " + COMPANY + " HRMS",
                buildTaskUpdatedBody(name, taskTitle, taskDescription, projectName, priority, startDate, dueDate, updatedBy));
    }

    // ═══════════════════════════════════════════════════════════════════
    // 9 — TASK COMPLETED (TEAM LEADER)
    // ═══════════════════════════════════════════════════════════════════

    @Async
    public void sendTaskCompletedToLeader(String email, String firstName, String lastName,
                                          String taskTitle, String projectName, String completedBy) {
        String name = firstName + " " + lastName;
        send(email,
                "Task Completed – " + COMPANY + " HRMS",
                buildTaskCompletedBody(name, taskTitle, projectName, completedBy));
    }

    // ═══════════════════════════════════════════════════════════════════
    // 10 — DOCUMENT READY
    // ═══════════════════════════════════════════════════════════════════

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
                    buildDocumentReadyBody(name, referenceId));
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
                    buildRequiredDocumentUploadedBody(name, documentLabel, fileName, replaced));
            log.info("HREmailService.sendRequiredDocumentUploaded result: recipientEmail={} referenceId={} subject={} result={}",
                    email, referenceId, subject, result);
            return result;
        } catch (Exception e) {
            log.error("HREmailService.sendRequiredDocumentUploaded exception: recipientEmail={} referenceId={} subject={} message={}",
                    email, referenceId, subject, e.getMessage(), e);
            return false;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 10 — AUTHORIZATION DECISION
    // ═══════════════════════════════════════════════════════════════════

    public boolean sendAuthorizationDecision(String email, String firstName, String lastName,
                                             String authorizationType, LocalDate startDate,
                                             LocalDate endDate, LocalDate absenceDate,
                                             LocalTime fromTime, LocalTime toTime,
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
                    buildAuthorizationDecisionBody(name, authorizationType, startDate, endDate,
                            absenceDate, fromTime, toTime,
                            processedAt, approved, decisionNote, referenceId));
            log.info("HREmailService.sendAuthorizationDecision result: recipientEmail={} requestId={} subject={} result={}",
                    email, requestId, subject, result);
            return result;
        } catch (Exception e) {
            log.error("HREmailService.sendAuthorizationDecision exception: recipientEmail={} requestId={} subject={} message={}",
                    email, requestId, subject, e.getMessage(), e);
            return false;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // INTERNAL SEND
    // ═══════════════════════════════════════════════════════════════════

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
            log.info("✅ Email sent to {} — {}", to, subject);
            return true;
        } catch (Exception e) {
            log.error("❌ Failed to send email: recipientEmail={} subject={} errorType={} message={}",
                    to, subject, e.getClass().getSimpleName(), e.getMessage(), e);
            return false;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // HTML BUILDERS
    // ═══════════════════════════════════════════════════════════════════

    private String buildRoleAssignedBody(String name, String role) {
        String roleLine = role != null && !role.isBlank()
                ? "Your access has been prepared for your role as <strong>" + role + "</strong>, so the platform will show the tools and areas relevant to your work."
                : "Your access has been prepared so the platform will show the tools and areas relevant to your work.";

        return wrap("Welcome to ArabSoft", "Your access is active", """
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                Dear <strong>%s</strong>,
            </p>
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                Welcome to <strong>ArabSoft</strong>. We are pleased to let you know
                that you are now officially onboarded and your access to the internal
                HR platform is active.
            </p>
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                You can now sign in to manage your HR requests, follow your work
                activities, and stay connected with the Human Resources team.
            </p>
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                %s
            </p>
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                We are glad to have you with us, and we wish you a smooth start
                with the team.
            </p>
            %s
            <p style="font-size:14px;color:#64748B;line-height:1.7;text-align:center;margin-top:24px;">
                For any access question, please contact the Human Resources Department.
            </p>
            """.formatted(name, roleLine,
                actionButton("Log in to ArabSoft", frontendUrl + "/login", "#16A34A")));
    }

    private String buildPromotionRoleAssignedBody(String name, String role, String assignedBy) {
        String assignedByLine = assignedBy != null && !assignedBy.isBlank()
                ? "This update was recorded by <strong>" + assignedBy + "</strong>."
                : "This update has been recorded in the HR platform.";

        return wrap("Role Update", "Your responsibilities and access have been updated", """
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                Dear <strong>%s</strong>,
            </p>
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                We are pleased to confirm that your role in <strong>ArabSoft</strong>
                has been updated to <strong>%s</strong>.
            </p>
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                Your platform access will now reflect the responsibilities and work
                areas associated with this role.
            </p>
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                %s
            </p>
            %s
            <p style="font-size:14px;color:#64748B;line-height:1.7;text-align:center;margin-top:24px;">
                If you need clarification about this change, please contact the Human Resources Department.
            </p>
            """.formatted(
                name,
                role,
                assignedByLine,
                actionButton("Open ArabSoft", frontendUrl + "/login", "#2563EB")
            ));
    }

    private String buildRoleUpdatedBody(String name, String oldRole, String newRole, String assignedBy) {
        String previousRoleLine = oldRole != null && !oldRole.isBlank() && !"NONE".equalsIgnoreCase(oldRole)
                ? "Your previous system role was <strong>" + oldRole + "</strong>."
                : "This change replaces your previous pending access state.";
        String assignedByLine = assignedBy != null && !assignedBy.isBlank()
                ? "The update was recorded by <strong>" + assignedBy + "</strong>."
                : "The update has been recorded in the HR platform.";

        return wrap("Role Update", "Your ArabSoft role has changed", """
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                Dear <strong>%s</strong>,
            </p>
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                Your system role in <strong>ArabSoft</strong> has been updated to
                <strong>%s</strong>.
            </p>
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                %s
            </p>
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                %s
            </p>
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                Your available areas and actions in the HR platform will follow this updated role.
            </p>
            %s
            <p style="font-size:14px;color:#64748B;line-height:1.7;text-align:center;margin-top:24px;">
                If you have any questions about the change, please contact the Human Resources Department.
            </p>
            """.formatted(
                name,
                newRole,
                previousRoleLine,
                assignedByLine,
                actionButton("Open ArabSoft", frontendUrl + "/login", "#2563EB")
            ));
    }

    private String buildLeaveApprovedBody(String name, String leaveType,
                                           LocalDate start, LocalDate end,
                                           int days, String refId) {
        return wrap("Leave Request Approved ✓", "Reference: " + refId, """
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                Dear <strong>%s</strong>,
            </p>
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                We are pleased to inform you that your leave request has been
                <strong style="color:#16A34A;">approved</strong>.
            </p>
            %s
            <p style="font-size:15px;color:#374151;line-height:1.8;margin-top:20px;">
                Please ensure that all your responsibilities are managed accordingly
                during your absence. If you have any questions, feel free to contact
                the HR department.
            </p>
            """.formatted(name,
                infoGrid(new String[][]{
                    {"Reference ID",  refId},
                    {"Leave Type",    leaveType},
                    {"Start Date",    start.format(DATE_FMT)},
                    {"End Date",      end.format(DATE_FMT)},
                    {"Duration",      days + " working day" + (days > 1 ? "s" : "")},
                })));
    }

    private String buildLeaveRejectedBody(String name, String leaveType,
                                           LocalDate start, LocalDate end,
                                           String reason, String refId) {
        return wrap("Leave Request Not Approved", "Reference: " + refId, """
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                Dear <strong>%s</strong>,
            </p>
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                We regret to inform you that your leave request has
                <strong style="color:#DC2626;">not been approved</strong>.
            </p>
            %s
            <div style="background:#FEF2F2;border-left:4px solid #EF4444;border-radius:8px;
                        padding:16px 20px;margin:20px 0;">
                <p style="margin:0;font-size:13px;color:#6B7280;font-weight:600;
                           text-transform:uppercase;letter-spacing:0.05em;">Decision Reason</p>
                <p style="margin:6px 0 0;font-size:15px;color:#7F1D1D;line-height:1.7;">%s</p>
            </div>
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                You may contact your Team Leader or the HR department for further clarification.
            </p>
            """.formatted(name,
                infoGrid(new String[][]{
                    {"Reference ID",  refId},
                    {"Leave Type",    leaveType},
                    {"Start Date",    start.format(DATE_FMT)},
                    {"End Date",      end.format(DATE_FMT)},
                }),
                reason != null ? reason : "Does not meet current company policy requirements."));
    }

    private String buildLoanApprovedBody(String name, double amount,
                                          int months, double installment,
                                          String refId) {
        return wrap("Loan Request Approved ✓", "Reference: " + refId, """
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                Dear <strong>%s</strong>,
            </p>
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                We are pleased to inform you that your loan request has been
                <strong style="color:#16A34A;">approved</strong>.
            </p>
            %s
            <p style="font-size:15px;color:#374151;line-height:1.8;margin-top:20px;">
                The repayment will be scheduled automatically via monthly payroll deductions.
                Please review your payslip for details each month.
            </p>
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                If you have any questions regarding your loan or repayment schedule,
                please contact the HR department.
            </p>
            """.formatted(name,
                infoGrid(new String[][]{
                    {"Reference ID",       refId},
                    {"Approved Amount",    String.format("%.0f TND", amount)},
                    {"Repayment Period",   months + " months"},
                    {"Monthly Deduction",  String.format("%.0f TND / month", installment)},
                })));
    }

    private String buildLoanRejectedBody(String name, double amount,
                                          String reason, String refId) {
        return wrap("Loan Request Not Approved", "Reference: " + refId, """
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                Dear <strong>%s</strong>,
            </p>
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                We regret to inform you that your loan request for
                <strong>%.0f TND</strong> has
                <strong style="color:#DC2626;">not been approved</strong>.
            </p>
            %s
            <div style="background:#FEF2F2;border-left:4px solid #EF4444;border-radius:8px;
                        padding:16px 20px;margin:20px 0;">
                <p style="margin:0;font-size:13px;color:#6B7280;font-weight:600;
                           text-transform:uppercase;letter-spacing:0.05em;">Decision Reason</p>
                <p style="margin:6px 0 0;font-size:15px;color:#7F1D1D;line-height:1.7;">%s</p>
            </div>
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                You may contact the HR department for further clarification or to discuss
                alternative options.
            </p>
            """.formatted(name, amount,
                infoGrid(new String[][]{
                    {"Reference ID",    refId},
                    {"Requested Amount",String.format("%.0f TND", amount)},
                }),
                reason != null ? reason : "Does not meet current loan eligibility criteria."));
    }

    private String buildLoanMeetingNotificationBody(String name, LocalDateTime meetingAt,
                                                    boolean updated, String refId) {
        String action = updated ? "updated" : "scheduled";
        return wrap(updated ? "Loan Meeting Updated" : "Loan Meeting Scheduled", "Reference: " + refId, """
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                Dear <strong>%s</strong>,
            </p>
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                Your loan meeting has been %s. Please check your loan request details
                for the date and time.
            </p>
            %s
            %s
            """.formatted(name, action,
                infoGrid(new String[][]{
                    {"Reference ID", refId},
                    {"Meeting", formatDateTimeOrDash(meetingAt)},
                    {"Status", updated ? "Updated" : "Scheduled"},
                }),
                actionButton("Open My Loans", frontendUrl + "/employee/loans", "#2563EB")));
    }

    private String buildLoanFinalFileReadyBody(String name, String refId) {
        return wrap("Final Loan Document Ready", "Reference: " + refId, """
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                Dear <strong>%s</strong>,
            </p>
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                Your final HR loan document is ready. You can download it from your
                loan history.
            </p>
            %s
            %s
            """.formatted(name,
                infoGrid(new String[][]{
                    {"Reference ID", refId},
                    {"Document", "Final HR loan document"},
                    {"Status", "Ready for download"},
                }),
                actionButton("Open My Loans", frontendUrl + "/employee/loans", "#2563EB")));
    }

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
                    ⏱ Expires at: %s
                </p>
            </div>
            <p style="font-size:14px;color:#6B7280;line-height:1.8;">
                Enter this code in the password reset page to set a new password.
                The code is valid for a limited time only.
            </p>
            <div style="background:#FFFBEB;border-left:4px solid #F59E0B;border-radius:8px;
                        padding:14px 18px;margin-top:20px;">
                <p style="margin:0;font-size:14px;color:#92400E;">
                    ⚠️ If you did not request this password reset, please ignore this email.
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
                    ⏱ Expires at: %s
                </p>
            </div>
            <p style="font-size:14px;color:#6B7280;line-height:1.8;">
                Enter this code in your profile to set a new password.
                The code is valid for a limited time only.
            </p>
            <div style="background:#FFFBEB;border-left:4px solid #F59E0B;border-radius:8px;
                        padding:14px 18px;margin-top:20px;">
                <p style="margin:0;font-size:14px;color:#92400E;">
                    ⚠️ If you did not request this password change, please contact HR immediately.
                </p>
            </div>
            """.formatted(name, otpCode, expiresAt));
    }

    private String buildTaskAssignedBody(String name, String taskTitle, String taskDescription, String projectName,
                                         String priority, LocalDate startDate, LocalDate dueDate, String assignedBy) {
        String due = dueDate != null ? dueDate.format(DATE_FMT) : "No due date";
        String start = startDate != null ? startDate.format(DATE_FMT) : "No start date";
        String description = (taskDescription != null && !taskDescription.isBlank()) ? taskDescription : "No description provided";
        String priorityLabel = priority != null ? priority.replace("_", " ") : "MEDIUM";
        String assignedByValue = (assignedBy != null && !assignedBy.isBlank()) ? assignedBy : "Team Leader";

        return wrap("New Task Assigned", "A task has been assigned to you", """
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                Dear <strong>%s</strong>,
            </p>
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                A new task has been assigned to you. Please review the details below and
                start working on it as soon as possible.
            </p>
            %s
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                If you need clarification, please contact your Team Leader.
            </p>
            %s
            """.formatted(name,
                infoGrid(new String[][]{
                    {"Task Title", taskTitle},
                    {"What to do", description},
                    {"Project", projectName},
                    {"Priority", priorityLabel},
                    {"Start Date", start},
                    {"Due Date", due},
                    {"Assigned By", assignedByValue}
                }),
                actionButton("Open My Tasks", frontendUrl + "/employee/tasks", "#2563EB")));
    }

    private String buildTeamAssignedBody(String name, String teamName) {
        return wrap("Team Assignment Updated", "You have been assigned to a team", """
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                Dear <strong>%s</strong>,
            </p>
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                HR has assigned you to the team below.
            </p>
            %s
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                You can review your profile for the latest assignment details.
            </p>
            %s
            """.formatted(name,
                infoGrid(new String[][]{
                    {"Team", teamName},
                    {"Status", "Assigned"}
                }),
                actionButton("Open My Profile", frontendUrl + "/employee/profile", "#2563EB")));
    }

    private String buildTeamChangedBody(String name, String oldTeamName, String newTeamName) {
        return wrap("Team Assignment Changed", "Your team assignment has changed", """
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                Dear <strong>%s</strong>,
            </p>
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                HR has updated your team assignment.
            </p>
            %s
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                You can review your profile for the latest assignment details.
            </p>
            %s
            """.formatted(name,
                infoGrid(new String[][]{
                    {"Previous Team", oldTeamName},
                    {"New Team", newTeamName}
                }),
                actionButton("Open My Profile", frontendUrl + "/employee/profile", "#2563EB")));
    }

    private String buildTeamRemovedBody(String name, String oldTeamName) {
        return wrap("Team Assignment Removed", "You have been removed from a team", """
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                Dear <strong>%s</strong>,
            </p>
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                HR has removed you from the team below.
            </p>
            %s
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                Please contact HR if you believe this assignment is incorrect.
            </p>
            %s
            """.formatted(name,
                infoGrid(new String[][]{
                    {"Previous Team", oldTeamName},
                    {"Status", "Removed"}
                }),
                actionButton("Open My Profile", frontendUrl + "/employee/profile", "#2563EB")));
    }

    private String buildTeamLeaderAssignedBody(String name, String teamName) {
        return wrap("Team Leader Assignment", "You have been assigned as Team Leader", """
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                Dear <strong>%s</strong>,
            </p>
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                HR has assigned you as Team Leader for the team below.
            </p>
            %s
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                You can now manage this team's members, tasks, and Team Leader-stage requests from your Team Leader area.
            </p>
            %s
            """.formatted(name,
                infoGrid(new String[][]{
                    {"Team", teamName},
                    {"Role", "Team Leader"}
                }),
                actionButton("Open Team Members", frontendUrl + "/team/members", "#2563EB")));
    }

    private String buildTeamLeaderRemovedBody(String name, String teamName) {
        return wrap("Team Leader Assignment Updated", "You are no longer Team Leader for this team", """
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                Dear <strong>%s</strong>,
            </p>
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                HR has updated the Team Leader assignment for the team below.
            </p>
            %s
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                Your Team Leader role remains active, but this team is no longer assigned to you.
            </p>
            """.formatted(name,
                infoGrid(new String[][]{
                    {"Team", teamName},
                    {"Status", "No longer Team Leader"}
                })));
    }

    private String buildTaskUpdatedBody(String name, String taskTitle, String taskDescription, String projectName,
                                        String priority, LocalDate startDate, LocalDate dueDate, String updatedBy) {
        String due = dueDate != null ? dueDate.format(DATE_FMT) : "No due date";
        String start = startDate != null ? startDate.format(DATE_FMT) : "No start date";
        String description = (taskDescription != null && !taskDescription.isBlank()) ? taskDescription : "No description provided";
        String projectValue = (projectName != null && !projectName.isBlank()) ? projectName : "No project";
        String priorityLabel = priority != null ? priority.replace("_", " ") : "MEDIUM";
        String updatedByValue = (updatedBy != null && !updatedBy.isBlank()) ? updatedBy : "Team Leader";

        return wrap("Task Updated", "A task assigned to you has been updated", """
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                Dear <strong>%s</strong>,
            </p>
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                A task assigned to you has been updated. Please review the latest details below.
            </p>
            %s
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                If you need clarification, please contact your Team Leader.
            </p>
            %s
            """.formatted(name,
                infoGrid(new String[][]{
                    {"Task Title", taskTitle},
                    {"What to do", description},
                    {"Project", projectValue},
                    {"Priority", priorityLabel},
                    {"Start Date", start},
                    {"Due Date", due},
                    {"Updated By", updatedByValue}
                }),
                actionButton("Open My Tasks", frontendUrl + "/employee/tasks", "#2563EB")));
    }

    private String buildTaskCompletedBody(String leaderName, String taskTitle, String projectName, String completedBy) {
        String projectValue = (projectName != null && !projectName.isBlank()) ? projectName : "No project";
        String completedByValue = (completedBy != null && !completedBy.isBlank()) ? completedBy : "Team member";
        return wrap("Task Completed", "A team task was marked done", """
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                Dear <strong>%s</strong>,
            </p>
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                A task has been marked as <strong style="color:#16A34A;">DONE</strong>.
            </p>
            %s
            %s
            """.formatted(
                leaderName,
                infoGrid(new String[][]{
                    {"Task Title", taskTitle},
                    {"Project", projectValue},
                    {"Completed By", completedByValue}
                }),
                actionButton("Open Team Tasks", frontendUrl + "/team/tasks", "#16A34A")
            ));
    }

    private String buildDocumentReadyBody(String name, String refId) {
        return wrap("Document Ready", "Reference: " + refId, """
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                Dear <strong>%s</strong>,
            </p>
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                Your document request has been completed by the Human Resources Department.
                The final official file is now ready for access from the ArabSoft platform.
            </p>
            %s
            <p style="font-size:15px;color:#374151;line-height:1.8;margin-top:20px;">
                You can download or consult the file from your documents area.
                Please quote the reference number below for any HR follow-up.
            </p>
            %s
            """.formatted(name,
                infoGrid(new String[][]{
                    {"Reference ID", refId},
                    {"Document", "HR document"},
                    {"Status", "Ready for download"},
                }),
                actionButton("Open My Documents", frontendUrl + "/employee/documents", "#2563EB")));
    }

    private String buildRequiredDocumentUploadedBody(String name, String documentLabel, String fileName, boolean replaced) {
        String document = documentLabel != null && !documentLabel.isBlank() ? documentLabel : "Required HR document";
        String uploadedFile = fileName != null && !fileName.isBlank() ? fileName : "Uploaded file";
        String actionText = replaced ? "updated" : "added";
        return wrap(replaced ? "Required HR Document Updated" : "Required HR Document Added", document + " is available", """
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                Dear <strong>%s</strong>,
            </p>
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                The Human Resources Department has %s your contract copy in your required HR documents.
                You can access it from your documents area.
            </p>
            %s
            %s
            """.formatted(name, actionText,
                infoGrid(new String[][]{
                    {"Document", document},
                    {"File", uploadedFile},
                    {"Status", "Available for download"},
                }),
                actionButton("Open My Documents", frontendUrl + "/employee/documents", "#2563EB")));
    }

    private String buildAuthorizationDecisionBody(String name, String authorizationType,
                                                  LocalDate startDate, LocalDate endDate,
                                                  LocalDate absenceDate, LocalTime fromTime,
                                                  LocalTime toTime,
                                                  LocalDateTime processedAt,
                                                  boolean approved, String decisionNote,
                                                  String refId) {
        String statusText = approved ? "approved" : "not approved";
        String statusColor = approved ? "#16A34A" : "#DC2626";
        String note = decisionNote != null && !decisionNote.isBlank()
                ? decisionNote
                : approved
                    ? "Approved according to the current HR workflow."
                    : "Does not meet current approval requirements.";
        String noteTitle = approved ? "HR Note" : "Decision Reason";

        return wrap(approved ? "Authorization Approved" : "Authorization Not Approved",
                "Reference: " + refId, """
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                Dear <strong>%s</strong>,
            </p>
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                Your authorization request has been
                <strong style="color:%s;">%s</strong>.
            </p>
            %s
            <div style="background:%s;border-left:4px solid %s;border-radius:8px;
                        padding:16px 20px;margin:20px 0;">
                <p style="margin:0;font-size:13px;color:#6B7280;font-weight:600;
                           text-transform:uppercase;letter-spacing:0.05em;">%s</p>
                <p style="margin:6px 0 0;font-size:15px;color:#374151;line-height:1.7;">%s</p>
            </div>
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                You can review the request status and details in your authorizations area.
            </p>
            %s
            """.formatted(name, statusColor, statusText,
                infoGrid(new String[][]{
                    {"Reference ID", refId},
                    {"Authorization Type", formatAuthorizationType(authorizationType)},
                    {"Requested Period", formatAuthorizationPeriod(authorizationType, startDate, endDate, absenceDate, fromTime, toTime)},
                    {"Status", approved ? "Approved" : "Rejected"},
                    {"Processed Date", formatDateTimeOrDash(processedAt)},
                }),
                approved ? "#F0FDF4" : "#FEF2F2",
                approved ? "#22C55E" : "#EF4444",
                noteTitle,
                note,
                actionButton("Open My Authorizations", frontendUrl + "/employee/authorizations",
                        approved ? "#16A34A" : "#DC2626")));
    }

    private String formatAuthorizationType(String authorizationType) {
        if ("TIME_PERMISSION".equals(authorizationType)) return "Short Absence Request";
        return formatEnum(authorizationType);
    }

    private String formatAuthorizationPeriod(String authorizationType, LocalDate startDate, LocalDate endDate,
                                             LocalDate absenceDate, LocalTime fromTime, LocalTime toTime) {
        if ("TIME_PERMISSION".equals(authorizationType)) {
            LocalDate date = absenceDate != null ? absenceDate : startDate;
            if (date == null) return "Not specified";
            if (fromTime != null && toTime != null) {
                return formatDateOrDash(date) + " · " + formatTime(fromTime) + "-" + formatTime(toTime);
            }
            return formatDateOrDash(date);
        }
        if (startDate == null && endDate == null) return "Not specified";
        if (startDate != null && endDate != null) {
            return formatDateOrDash(startDate) + " to " + formatDateOrDash(endDate);
        }
        if (startDate != null) return "From " + formatDateOrDash(startDate);
        return "Until " + formatDateOrDash(endDate);
    }

    private String formatTime(LocalTime time) {
        return time == null ? "" : time.toString().substring(0, 5);
    }

    // ═══════════════════════════════════════════════════════════════════
    // SHARED LAYOUT COMPONENTS
    // ═══════════════════════════════════════════════════════════════════

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

    private String formatRole(String role) {
        return switch (role) {
            case "EMPLOYEE"    -> "Employee";
            case "TEAM_LEADER" -> "Team Leader";
            case "HR_MANAGER"  -> "HR Manager";
            default -> role;
        };
    }

    private String formatEnum(String value) {
        if (value == null || value.isBlank()) return "—";
        String[] words = value.split("_");
        StringBuilder label = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) continue;
            if (!label.isEmpty()) label.append(' ');
            label.append(word.charAt(0)).append(word.substring(1).toLowerCase());
        }
        return label.toString();
    }

    private String formatDateOrDash(LocalDate date) {
        return date != null ? date.format(DATE_FMT) : "—";
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
