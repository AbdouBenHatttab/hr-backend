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
    private static final String SYSTEM_NAME = "ArabSoft Human Resources Management System";
    private static final String DEPARTMENT  = "Human Resources Department";
    private static final String COMPANY     = "ArabSoft";

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@arabsoft-hrms.com}")
    private String fromEmail;

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
                                  String username, String newRole) {
        String name    = firstName + " " + lastName;
        String roleLabel = formatRole(newRole);
        send(email,
            "Account Activated – " + COMPANY + " HRMS",
            buildRoleAssignedBody(name, username, roleLabel));
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

    @Async
    public void sendLoanApproved(String email, String firstName, String lastName,
                                  double amount, int months, double monthlyInstallment,
                                  String referenceId) {
        String name = firstName + " " + lastName;
        send(email,
            "Loan Request Approved – " + referenceId,
            buildLoanApprovedBody(name, amount, months, monthlyInstallment, referenceId));
    }

    // ═══════════════════════════════════════════════════════════════════
    // 5 — LOAN REJECTED
    // ═══════════════════════════════════════════════════════════════════

    @Async
    public void sendLoanRejected(String email, String firstName, String lastName,
                                  double amount, String reason, String referenceId) {
        String name = firstName + " " + lastName;
        send(email,
            "Loan Request Not Approved – " + referenceId,
            buildLoanRejectedBody(name, amount, reason, referenceId));
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

    // ═══════════════════════════════════════════════════════════════════
    // INTERNAL SEND
    // ═══════════════════════════════════════════════════════════════════

    private void send(String to, String subject, String html) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(fromEmail, COMPANY + " HRMS");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);

            ClassPathResource logo = new ClassPathResource("static/images/logo.jpg");
            if (logo.exists()) helper.addInline("logo", logo);

            mailSender.send(msg);
            log.info("✅ Email sent to {} — {}", to, subject);
        } catch (Exception e) {
            log.error("❌ Failed to send email to {}: {}", to, e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // HTML BUILDERS
    // ═══════════════════════════════════════════════════════════════════

    private String buildRoleAssignedBody(String name, String username, String role) {
        return wrap("Account Activated", "Your account is now active", """
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                Dear <strong>%s</strong>,
            </p>
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                We are pleased to inform you that your account on the
                <strong>ArabSoft Human Resources Management System</strong>
                has been activated. Your assigned role is:
            </p>
            <div style="background:#EFF6FF;border-left:4px solid #2563EB;border-radius:8px;
                        padding:16px 20px;margin:24px 0;">
                <p style="margin:0;font-size:14px;color:#6B7280;">Role</p>
                <p style="margin:4px 0 0;font-size:20px;font-weight:700;color:#1D4ED8;">%s</p>
            </div>
            <div style="background:#F9FAFB;border:1px solid #E5E7EB;border-radius:8px;
                        padding:16px 20px;margin:16px 0;">
                <p style="margin:0;font-size:13px;color:#6B7280;">Username</p>
                <p style="margin:4px 0 0;font-size:16px;font-weight:600;color:#111827;
                           font-family:monospace;">%s</p>
            </div>
            <p style="font-size:15px;color:#374151;line-height:1.8;">
                You can now log in and access all features available to your role.
                If you have any questions, please contact the HR department.
            </p>
            %s
            """.formatted(name, role, username,
                actionButton("Log In to HRMS", frontendUrl + "/login", "#16A34A")));
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
                          ArabSoft Human Resources Management System
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
                      Human Resources Department · ArabSoft
                    </p>
                    <p style="margin:4px 0 0;font-size:12px;color:#64748B;">
                      ArabSoft Human Resources Management System
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
            """.formatted(logoTag, title, subtitle, content);
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
}
