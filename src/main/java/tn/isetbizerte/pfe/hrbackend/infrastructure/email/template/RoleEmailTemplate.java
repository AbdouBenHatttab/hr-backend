package tn.isetbizerte.pfe.hrbackend.infrastructure.email.template;

import org.springframework.core.io.ClassPathResource;

/** Builds role/setup email HTML bodies only; it does not send email. */
public class RoleEmailTemplate {

    private static final String SYSTEM_NAME = "ArabSoft Human Resources Management System";

    public String buildRoleAssignedBody(String name, String role, String frontendUrl, String fromDisplayName) {
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
                actionButton("Log in to ArabSoft", frontendUrl + "/login", "#16A34A")),
                fromDisplayName);
    }

    public String buildPromotionRoleAssignedBody(String name, String role, String assignedBy,
                                                 String frontendUrl, String fromDisplayName) {
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
            ),
                fromDisplayName);
    }

    public String buildRoleUpdatedBody(String name, String oldRole, String newRole, String assignedBy,
                                       String frontendUrl, String fromDisplayName) {
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
            ),
                fromDisplayName);
    }

    public String formatRole(String role) {
        return switch (role) {
            case "EMPLOYEE"    -> "Employee";
            case "TEAM_LEADER" -> "Team Leader";
            case "HR_MANAGER"  -> "HR Manager";
            default -> role;
        };
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

    private String actionButton(String label, String url, String color) {
        return "<div style=\"text-align:center;margin-top:28px;\">" +
               "<a href=\"" + url + "\" style=\"background:" + color + ";color:white;" +
               "text-decoration:none;padding:14px 32px;border-radius:8px;" +
               "font-size:15px;font-weight:600;display:inline-block;\">" +
               label + "</a></div>";
    }
}
