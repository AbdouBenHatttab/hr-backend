package tn.isetbizerte.pfe.hrbackend.infrastructure.email.template;

import org.springframework.core.io.ClassPathResource;

/** Builds loan email HTML only; HREmailService remains responsible for sending. */
public class LoanEmailTemplate {

    private static final String SYSTEM_NAME = "ArabSoft Human Resources Management System";

    public String buildLoanApprovedBody(String name, double amount, int months, double installment,
                                        String refId, String fromDisplayName) {
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
                })),
                fromDisplayName);
    }

    public String buildLoanRejectedBody(String name, double amount, String reason, String refId,
                                        String fromDisplayName) {
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
                reason != null ? reason : "Does not meet current loan eligibility criteria."),
                fromDisplayName);
    }

    public String buildLoanMeetingNotificationBody(String name, String meetingAt,
                                                   boolean updated, String refId,
                                                   String frontendUrl, String fromDisplayName) {
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
                    {"Meeting", meetingAt},
                    {"Status", updated ? "Updated" : "Scheduled"},
                }),
                actionButton("Open My Loans", frontendUrl + "/employee/loans", "#2563EB")),
                fromDisplayName);
    }

    public String buildLoanFinalFileReadyBody(String name, String refId, String frontendUrl,
                                              String fromDisplayName) {
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
                actionButton("Open My Loans", frontendUrl + "/employee/loans", "#2563EB")),
                fromDisplayName);
    }

    private String wrap(String title, String subtitle, String content, String fromDisplayName) {
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

    private String actionButton(String label, String url, String color) {
        return "<div style=\"text-align:center;margin-top:28px;\">" +
               "<a href=\"" + url + "\" style=\"background:" + color + ";color:white;" +
               "text-decoration:none;padding:14px 32px;border-radius:8px;" +
               "font-size:15px;font-weight:600;display:inline-block;\">" +
               label + "</a></div>";
    }
}
