package tn.isetbizerte.pfe.hrbackend.infrastructure.email.template;

import org.springframework.core.io.ClassPathResource;

/** Builds team email HTML only; HREmailService remains responsible for sending. */
public class TeamEmailTemplate {

    private static final String SYSTEM_NAME = "ArabSoft Human Resources Management System";

    public String buildTeamAssignedBody(String name, String teamName, String frontendUrl, String fromDisplayName) {
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
                actionButton("Open My Profile", frontendUrl + "/employee/profile", "#2563EB")),
                fromDisplayName);
    }

    public String buildTeamChangedBody(String name, String oldTeamName, String newTeamName, String frontendUrl, String fromDisplayName) {
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
                actionButton("Open My Profile", frontendUrl + "/employee/profile", "#2563EB")),
                fromDisplayName);
    }

    public String buildTeamRemovedBody(String name, String oldTeamName, String frontendUrl, String fromDisplayName) {
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
                actionButton("Open My Profile", frontendUrl + "/employee/profile", "#2563EB")),
                fromDisplayName);
    }

    public String buildTeamLeaderAssignedBody(String name, String teamName, String frontendUrl, String fromDisplayName) {
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
                actionButton("Open Team Members", frontendUrl + "/team/members", "#2563EB")),
                fromDisplayName);
    }

    public String buildTeamLeaderRemovedBody(String name, String teamName, String fromDisplayName) {
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
                })),
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
