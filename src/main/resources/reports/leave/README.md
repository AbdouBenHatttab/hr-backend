# Leave Approval Report Template

This folder contains Jasper templates for leave reporting.

- `leave_approval.jrxml`: PDF template used by `infrastructure.reporting.LeaveReportService`.

The report is generated only when a leave request is fully approved:

- `status = APPROVED`
- `teamLeaderDecision = APPROVED`
- `hrDecision = APPROVED`

