# Team Leader Backend Guide

This README summarizes Team Leader capabilities and the related backend endpoints, payloads, and rules.

## Role Access
- Role: `TEAM_LEADER`
- Authentication: JWT validated by Spring Security
- Public endpoints: `/public/**` (not role‑restricted)

## Roles & Access Model
- `EMPLOYEE`
- `TEAM_LEADER`
- `HR_MANAGER`

## Scope Separation (Non‑Negotiable)
Personal scope: `/api/employee/**`
- Used by: `EMPLOYEE`, `TEAM_LEADER`, `HR_MANAGER` (optional)
- Covers: leave requests, loans, documents, profile, personal tasks (if any)
- Rule: **NEVER** performs actions on other users

Management scope: `/api/leader/**`
- Used ONLY by: `TEAM_LEADER`
- Covers: team members, team leave approvals, task assignment, performance
- Rule: **ALWAYS** targets team members

## Workflow Routing Rules (Table)
| Actor | Step 1 | Step 2 | Notes |
| --- | --- | --- | --- |
| EMPLOYEE | TEAM_LEADER | HR_MANAGER | Standard flow |
| TEAM_LEADER | HR_MANAGER | HR_MANAGER | **Skip TL step** |

**Explicit rule:** TEAM_LEADER requests bypass team‑level approval entirely.

## Global Enforcement Rules (System Constraints)
All approval endpoints **MUST** enforce:
- `actor ≠ target user` (no self‑approval for leave/loan/task)

All team actions **MUST** validate:
- employee belongs to leader’s team

Violations **MUST** throw:
- `AccessDeniedException` or `ForbiddenException`

## Service Layer Responsibilities (Mandatory)
- Controller: routing only
- Service: business rules + routing + validation
- SecurityConfig: role access
- Repository: data only
- **Shared services** (LeaveService, LoanService, etc.) are reused by EMPLOYEE and TEAM_LEADER
- **Transaction boundaries are required**:
  - Example: approve leave → save decision → publish Kafka event (atomic)

## Leave Requests (Team Leader Approval)
Base path: `/api/employee/leave`

### Personal Leave Request (Employee Scope)
- Team Leader uses the same endpoints as employees for their own requests.
- No `/leader/leave` duplication.
- Rule: TL cannot approve their own leave.
- Routing rule: TL leave should go directly to HR (or another TL if supported).

### Get Team Leave Requests (All Statuses)
```http
GET /api/employee/leave/my-team
Authorization: Bearer <TEAM_LEADER_TOKEN>
```
- Returns all leave requests from the leader’s team.

### Approve Leave Request
```http
POST /api/employee/leave/{leaveId}/team-leader/approve
Authorization: Bearer <TEAM_LEADER_TOKEN>
```
- Only the team leader of the employee can approve.
- Status remains `PENDING` until HR final approval.

### Reject Leave Request
```http
POST /api/employee/leave/{leaveId}/team-leader/reject
Authorization: Bearer <TEAM_LEADER_TOKEN>
Content-Type: application/json

{ "reason": "Not eligible this period" }
```
- Rejection reason is required.
- Sets `teamLeaderDecision` to `REJECTED`.

## Team Management (Leader Side)
Base path: `/api/leader`

### Get My Team
```http
GET /api/leader/team
Authorization: Bearer <TEAM_LEADER_TOKEN>
```
- Returns leader’s team and members.

### Get Available Employees (Unassigned)
```http
GET /api/leader/available-employees
Authorization: Bearer <TEAM_LEADER_TOKEN>
```

### Add Team Member
```http
POST /api/leader/team/members
Authorization: Bearer <TEAM_LEADER_TOKEN>
Content-Type: application/json

{ "employeeId": 123 }
```

### Remove Team Member
```http
DELETE /api/leader/team/members/{employeeId}
Authorization: Bearer <TEAM_LEADER_TOKEN>
```

## Projects & Tasks (Team Leader)
Base path: `/api/leader`

### Projects
```http
GET /api/leader/projects
POST /api/leader/projects
DELETE /api/leader/projects/{projectId}
```

### Tasks
```http
POST /api/leader/projects/{projectId}/tasks
DELETE /api/leader/tasks/{taskId}
```

## Task Access (Team Leader or Employee)
Base path: `/api/employee/tasks`

```http
GET /api/employee/tasks
PATCH /api/employee/tasks/{taskId}/status
GET /api/employee/tasks/{taskId}
```

## Notifications
```http
GET /api/notifications
PATCH /api/notifications/{id}/read
PATCH /api/notifications/read-all
```

## PDF Access (Approved Only)
```http
GET /api/reports/leave/{leaveId}
Authorization: Bearer <TEAM_LEADER_TOKEN>
```
- Only for fully approved leave requests.
- Team leader must belong to the employee’s team.

## Key Rules and Notes
- Team leaders can approve/reject leave requests for their team only.
- TL cannot approve their own leave or loan.
- Personal actions (leave/loan/doc requests) use employee endpoints.
- HR final approval is still required for leave PDFs.
- Team leaders can manage their team’s members and tasks.

## See Also
- Full API reference: `DOC_BACKEND_API.md`
- Role mapping guide: `DOCUMENT_ROLE_GUIDE.md`
