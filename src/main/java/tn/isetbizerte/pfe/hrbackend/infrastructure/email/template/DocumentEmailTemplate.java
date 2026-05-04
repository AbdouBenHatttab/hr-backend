package tn.isetbizerte.pfe.hrbackend.infrastructure.email.template;

import org.springframework.core.io.ClassPathResource;

/** Builds document email HTML bodies only; it does not send email. */
public class DocumentEmailTemplate {

    private static final String SYSTEM_NAME = "ArabSoft Human Resources Management System";

    public String buildDocumentReadyBody(String name, String refId, String frontendUrl, String fromDisplayName) {
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
                actionButton("Open My Documents", frontendUrl + "/employee/documents", "#2563EB")),
                fromDisplayName);
    }

    public String buildRequiredDocumentUploadedBody(String name, String documentLabel, String fileName,
                                                    boolean replaced, String frontendUrl, String fromDisplayName) {
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
                actionButton("Open My Documents", frontendUrl + "/employee/documents", "#2563EB")),
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
