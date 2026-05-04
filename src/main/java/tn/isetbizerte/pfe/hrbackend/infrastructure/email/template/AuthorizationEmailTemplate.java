package tn.isetbizerte.pfe.hrbackend.infrastructure.email.template;

import org.springframework.core.io.ClassPathResource;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Builds authorization email HTML bodies only; it does not send email. */
public class AuthorizationEmailTemplate {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String SYSTEM_NAME = "ArabSoft Human Resources Management System";

    public String buildAuthorizationDecisionBody(String name, String authorizationType,
                                                 LocalDate startDate, LocalDate endDate,
                                                 LocalDate absenceDate, LocalTime fromTime,
                                                 LocalTime toTime,
                                                 String equipmentType,
                                                 String processedAt,
                                                 boolean approved, String decisionNote,
                                                 String refId, String frontendUrl,
                                                 String fromDisplayName) {
        String statusText = approved ? "approved" : "not approved";
        String statusColor = approved ? "#16A34A" : "#DC2626";
        String note = decisionNote != null && !decisionNote.isBlank()
                ? decisionNote
                : approved
                    ? "Approved according to the current HR workflow."
                    : "Does not meet current approval requirements.";
        String noteTitle = approved ? "HR Note" : "Decision Reason";
        List<String[]> detailRows = new ArrayList<>();
        detailRows.add(new String[]{"Reference ID", refId});
        detailRows.add(new String[]{"Authorization Type", formatAuthorizationType(authorizationType)});
        if (equipmentType != null && !equipmentType.isBlank()) {
            detailRows.add(new String[]{"Equipment Type", equipmentType});
        }
        detailRows.add(new String[]{"Requested Period", formatAuthorizationPeriod(authorizationType, startDate, endDate, absenceDate, fromTime, toTime)});
        detailRows.add(new String[]{"Status", approved ? "Approved" : "Rejected"});
        detailRows.add(new String[]{"Processed Date", processedAt});

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
                infoGrid(detailRows.toArray(new String[0][])),
                approved ? "#F0FDF4" : "#FEF2F2",
                approved ? "#22C55E" : "#EF4444",
                noteTitle,
                note,
                actionButton("Open My Authorizations", frontendUrl + "/employee/authorizations",
                        approved ? "#16A34A" : "#DC2626")),
                fromDisplayName);
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

    private String formatTime(LocalTime time) {
        return time == null ? "" : time.toString().substring(0, 5);
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
