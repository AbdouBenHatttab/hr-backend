package tn.isetbizerte.pfe.hrbackend.infrastructure.email.template;

import org.springframework.core.io.ClassPathResource;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Builds task email HTML bodies only; it does not send email. */
public class TaskEmailTemplate {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String SYSTEM_NAME = "ArabSoft Human Resources Management System";

    public String buildTaskAssignedBody(String name, String taskTitle, String taskDescription, String projectName,
                                        String priority, LocalDate startDate, LocalDate dueDate,
                                        String assignedBy, String frontendUrl, String fromDisplayName) {
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
                actionButton("Open My Tasks", frontendUrl + "/employee/tasks", "#2563EB")),
                fromDisplayName);
    }

    public String buildTaskUpdatedBody(String name, String taskTitle, String taskDescription, String projectName,
                                       String priority, LocalDate startDate, LocalDate dueDate,
                                       String updatedBy, String frontendUrl, String fromDisplayName) {
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
                actionButton("Open My Tasks", frontendUrl + "/employee/tasks", "#2563EB")),
                fromDisplayName);
    }

    public String buildTaskCompletedBody(String leaderName, String taskTitle, String projectName,
                                         String completedBy, String frontendUrl, String fromDisplayName) {
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
            ),
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
