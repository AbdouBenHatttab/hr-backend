# Backend API and Logic Documentation

This document describes all backend endpoints, payloads, roles, and business rules as implemented now.

## Roles and Auth Model
- Roles used in backend: `HR_MANAGER`, `TEAM_LEADER`, `EMPLOYEE`, `NEW_USER`.
- Security is enforced by Spring Security in `SecurityConfig`.
- JWT resource server validates access tokens issued by Keycloak.
- Public endpoints live under `/public/**`.
- Role-based access:
  - `HR_MANAGER` for HR endpoints.
  - `TEAM_LEADER` for team leader endpoints.
  - `EMPLOYEE` for employee endpoints.
  - `NEW_USER` for pending waiting endpoint.

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
- Idempotency guards (history-based) to prevent duplicate decisions.
  - If a decision already exists for (requestId, action, actorId) → reject.
  - Decisions are state-based: only `PENDING` can transition to `APPROVED` or `REJECTED`.

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
  - Example: approve leave → save decision → persist outbox event (atomic)
- **Outbox pattern is enforced**:
  - Events are persisted in `outbox_events` table inside the same DB transaction.
  - A scheduled publisher reads outbox and publishes to Kafka.

## Authentication and Password Reset
### POST `/public/auth/login`
- Body: `{ "username": "...", "password": "..." }`
- Response: `{ success, accessToken, refreshToken, tokenType, expiresIn, username, email, roles }`
- Logic:
  - Calls Keycloak token endpoint with `grant_type=password`.
  - Extracts role from JWT (`realm_access.roles`) and syncs role to DB.

### POST `/public/auth/refresh`
- Body: `{ "refreshToken": "..." }`
- Response: same structure as login.
- Logic:
  - Calls Keycloak with `grant_type=refresh_token`.
  - Returns new access token and refresh token if rotated.

### POST `/public/auth/register`
- Body: `{ username, password, firstName, lastName, email, phone, birthDate, address, maritalStatus, numberOfChildren }`
- Response: `{ success, message, username }`
- Logic:
  - Creates user in Keycloak (admin API), then persists locally.

### POST `/public/auth/forgot-password`
- Body: `{ email }`
- Response: `{ success, message }`
- Logic:
  - Generates and emails a reset token.
  - Frontend resend uses the same endpoint and target email.

### POST `/public/auth/reset-password`
- Body: `{ token, newPassword }`
- Response: `{ success, message }`

## Profile and User Info
### GET `/api/me`
- Roles: `EMPLOYEE`, `TEAM_LEADER`, `HR_MANAGER`
- Response: user profile + `personalInfo` fields if present.

### PATCH `/api/me`
- Roles: `EMPLOYEE`, `TEAM_LEADER`, `HR_MANAGER`
- Body: `{ phone, address, maritalStatus, numberOfChildren, avatarPhoto, avatarColor, department }`
- Rules: Only editable fields; name/email/username immutable.

### PATCH `/api/hr/users/{userId}/employment`
- Role: `HR_MANAGER`
- Body: `{ salary, hireDate, department }`

### GET `/api/new-user/waiting`
- Role: `NEW_USER`
- Response: waiting status and role.

### GET `/api/employee/info`
- Roles: `EMPLOYEE`, `TEAM_LEADER`, `HR_MANAGER`
- Response: username + role.

### GET `/api/leader/info`
- Roles: `TEAM_LEADER`, `HR_MANAGER`
- Response: username + role.

## Leave Requests (Employee and Team Leader)
Base path: `/api/employee/leave`

### Role Routing Rules (Critical)
- `TEAM_LEADER` uses the same employee endpoints for personal requests.
- Do not create `/leader/leave`, `/leader/loans`, `/leader/requests` for personal actions.
- TL cannot approve their own leave.
- TL leave must route directly to HR approval.

### POST `/request`
- Roles: `EMPLOYEE`, `TEAM_LEADER`
- Body: `{ leaveType, startDate, endDate, numberOfDays, reason }`
- Rules:
  - Dates must be valid; `numberOfDays` must match range.
  - Max 30 days per request.
  - Team Leader requests skip TL approval step.
  - Scoring engine runs at creation.

### GET `/my-requests`
- Roles: `EMPLOYEE`, `TEAM_LEADER`
- Returns own requests.
- Pagination: `?page=0&size=10`

### GET `/{leaveId}`
- Roles: `EMPLOYEE`, `TEAM_LEADER`, `HR_MANAGER`
- Access rule:
  - Owner OR Team Leader of owner OR HR can view.

### GET `/pending`
- Role: `TEAM_LEADER`
- Returns pending requests from leader’s team only.
- Pagination: `?page=0&size=10`
- Team leader own requests are excluded from team endpoints.

### GET `/my-team`
- Role: `TEAM_LEADER`
- Returns all requests from leader’s team (any status).
- Pagination: `?page=0&size=10`
- Team leader own requests are excluded from team endpoints.

### GET `/all`
- Role: `HR_MANAGER`
- Returns all requests across company.
- Pagination: `?page=0&size=10`

### POST `/{leaveId}/team-leader/approve`
- Role: `TEAM_LEADER`
- Rules:
  - Only team leader of the employee can approve.
  - Status remains `PENDING` until HR approves.
  - Team leader cannot approve their own leave.

### POST `/{leaveId}/team-leader/reject`
- Role: `TEAM_LEADER`
- Body: `{ reason }`
- Rules:
  - Rejection reason is mandatory.
  - Sets `rejectedBy` to TL keycloak ID.
  - Team leader cannot reject their own leave.

### POST `/{leaveId}/hr/approve`
- Role: `HR_MANAGER`
- Rules:
  - Only after TL approval (or TL skip for TL requests).
  - Sets `approvedBy` to HR keycloak ID.
  - Generates verification token for QR.
  - HR cannot approve their own leave.

### POST `/{leaveId}/hr/reject`
- Role: `HR_MANAGER`
- Body: `{ reason }`
- Rules:
  - Rejection reason is mandatory.
  - Sets `rejectedBy` to HR keycloak ID.
  - HR cannot reject their own leave.

### Leave Response Mapping
- Includes `employeeUsername` for UI identity rules.
- Includes computed `approvalStage` for consistent UI stage display.

## Document Requests
### POST `/api/employee/documents`
- Roles: `EMPLOYEE`, `TEAM_LEADER`, `HR_MANAGER`
- Body: `{ documentType, notes }`
- Team Leader uses the same endpoints for personal document/certificate requests.

### GET `/api/employee/documents`
- Roles: `EMPLOYEE`, `TEAM_LEADER`, `HR_MANAGER`
- Returns own document requests.
- Pagination: `?page=0&size=10`

### GET `/api/hr/documents`
- Role: `HR_MANAGER`
- Pagination: `?page=0&size=10`

### POST `/api/hr/documents/{id}/approve`
- Role: `HR_MANAGER`
- Body: `{ hrNote }`
- Sets `approvedBy`, generates verification token.
- HR cannot approve their own request.

### POST `/api/hr/documents/{id}/reject`
- Role: `HR_MANAGER`
- Body: `{ hrNote }`
- Sets `rejectedBy`.
- HR cannot reject their own request.

## Loan Requests
### POST `/api/employee/loans`
- Roles: `EMPLOYEE`, `TEAM_LEADER`, `HR_MANAGER`
- Body: `{ loanType, amount, repaymentMonths, reason }`
- Validation:
  - amount > 0, months 1–120, loanType enum, reason required.
- Eligibility rules:
  - Salary must be set.
  - Amount <= 3x salary.
  - No existing pending/approved loan.
  - Minimum 6 months employment.
- Scoring:
  - Calculates monthly installment, risk score, recommendation, decision reason.
  - Auto-reject if deductions exceed 40%.

### GET `/api/employee/loans`
- Roles: `EMPLOYEE`, `TEAM_LEADER`, `HR_MANAGER`
- Returns own loans.
- Pagination: `?page=0&size=10`

### GET `/api/employee/loans/eligibility`
- Roles: `EMPLOYEE`, `TEAM_LEADER`, `HR_MANAGER`
- Returns eligibility info and reasons.

### GET `/api/hr/loans`
- Role: `HR_MANAGER`
- Pagination: `?page=0&size=10`

### POST `/api/hr/loans/{id}/approve`
- Role: `HR_MANAGER`
- Body: `{ hrNote }`
- Sets `approvedBy`, generates verification token, updates monthly deductions.
- HR cannot approve their own request.

### POST `/api/hr/loans/{id}/reject`
- Role: `HR_MANAGER`
- Body: `{ hrNote }`
- Sets `rejectedBy`.
- HR cannot reject their own request.

## Authorization Requests
### POST `/api/employee/authorizations`
- Roles: `EMPLOYEE`, `TEAM_LEADER`, `HR_MANAGER`
- Body: `{ authorizationType, startDate, endDate, reason }`

### GET `/api/employee/authorizations`
- Roles: `EMPLOYEE`, `TEAM_LEADER`, `HR_MANAGER`
- Returns own authorizations.
- Pagination: `?page=0&size=10`

### GET `/api/hr/authorizations`
- Role: `HR_MANAGER`
- Pagination: `?page=0&size=10`

### POST `/api/hr/authorizations/{id}/approve`
- Role: `HR_MANAGER`
- Body: `{ hrNote }`
- Sets `approvedBy`, generates verification token.
- HR cannot approve their own request.

### POST `/api/hr/authorizations/{id}/reject`
- Role: `HR_MANAGER`
- Body: `{ hrNote }`
- Sets `rejectedBy`.
- HR cannot reject their own request.

## Reports and PDF Access
### GET `/api/reports/leave/{leaveId}`
- Roles: `EMPLOYEE`, `TEAM_LEADER`, `HR_MANAGER`
- Rules:
  - Must be fully approved (TL + HR).
  - Owner, team leader of owner, or HR.

### GET `/api/reports/documents/{id}`
### GET `/api/reports/loans/{id}`
### GET `/api/reports/authorizations/{id}`
- Roles: `EMPLOYEE`, `TEAM_LEADER`, `HR_MANAGER`
- Rules:
  - Must be approved.
  - Owner or HR only.

## Public Verification
### GET `/public/verify/{token}`
- Public, no auth.
- Supports verification for leave, document, loan, and authorization tokens.

## HR Management
### GET `/api/hr/dashboard`
- Role: `HR_MANAGER`
- Returns statistics.

### GET `/api/hr/users`
- Role: `HR_MANAGER`
- Returns all users with details.
- Pagination: `?page=0&size=10`

### GET `/api/hr/login-history`
- Role: `HR_MANAGER`
- Pagination: `?page=0&size=10`

### GET `/api/hr/pending-approvals`
- Role: `HR_MANAGER`
- Returns NEW_USER accounts awaiting role assignment.
- Pagination: `?page=0&size=10`

### POST `/api/hr/assign-role`
- Role: `HR_MANAGER`
- Body: `{ userId, role }`

### PATCH `/api/hr/users/{userId}/activate`
### PATCH `/api/hr/users/{userId}/deactivate`
- Role: `HR_MANAGER`

## Team Management
### POST `/api/hr/teams`
- Role: `HR_MANAGER`
- Body: `{ name, description, teamLeaderId }`

### GET `/api/hr/teams`
### GET `/api/hr/teams/{teamId}`
- Role: `HR_MANAGER`
- Pagination (list): `?page=0&size=10`

### GET `/api/leader/team`
- Role: `TEAM_LEADER`
- Returns leader’s team and members.

### GET `/api/leader/available-employees`
- Role: `TEAM_LEADER`
- Returns employees not assigned to any team.

### POST `/api/leader/team/members`
- Role: `TEAM_LEADER`
- Body: `{ employeeId }`

### DELETE `/api/leader/team/members/{employeeId}`
- Role: `TEAM_LEADER`

## Tasks and Projects
### GET `/api/leader/projects`
### POST `/api/leader/projects`
### DELETE `/api/leader/projects/{projectId}`
- Role: `TEAM_LEADER`

### POST `/api/leader/projects/{projectId}/tasks`
### DELETE `/api/leader/tasks/{taskId}`
- Role: `TEAM_LEADER`

### GET `/api/employee/tasks`
### PATCH `/api/employee/tasks/{taskId}/status`
- Roles: `EMPLOYEE`, `TEAM_LEADER`

### GET `/api/employee/tasks/{taskId}`
- Roles: `EMPLOYEE`, `TEAM_LEADER`
- Returns full details of one task (title, description, dates, project, priority, status, assignee).

## Concurrency and Validation
- Optimistic locking (`@Version`) on leave, loan, document, authorization.
- `409 Conflict` returned on concurrent decision updates.
- Validation errors return `400` with first field error message.

## Data Audit Fields
- `approvedBy` and `rejectedBy` stored for leave, loan, document, authorization.
- Returned in leave and request list mappings.

## Notifications
- Table: `notifications`
- Fields: `id`, `user_id`, `message`, `type`, `reference_type`, `reference_id`, `action_url`, `read`, `created_at`
- API:
  - `GET /api/notifications`
  - `PATCH /api/notifications/{id}/read`
  - `PATCH /api/notifications/read-all`
  - `PATCH /api/notifications/read-batch`
- Rules:
  - Ownership enforced when marking read.
  - Batch read rejects any ID not owned by the user.
- Task assignment notification:
  - `type = TASK_ASSIGNED`
  - `referenceType = TASK`, `referenceId = taskId`, `actionUrl = /employee/tasks?taskId={id}`

## Event Reliability (Kafka Consumers)
- Deduplication: events include `eventId`; consumers store processed IDs in `processed_events` to avoid duplicates.
- Retry + DLQ:
  - Consumer retry with backoff (3 retries, 1s).
  - Failed messages go to `{topic}.DLQ`.

## Request History (Audit)
- Table: `request_history`
- Fields: `id`, `request_id`, `type`, `action`, `actor_id`, `comment`, `timestamp`
- Written on create/approve/reject for leave, loan, document, authorization.

## Kafka Events
- `request-events` published on request decisions.
- `notification-events` consumed to persist notifications.

## Outbox (Kafka + DB Atomicity)
- Table: `outbox_events`
- Fields: `topic`, `event_key`, `payload`, `status`, `attempts`, `last_error`, `created_at`, `sent_at`
- Statuses: `PENDING`, `SENT`, `FAILED`
- Publisher:
  - Runs on a schedule (`app.outbox.publish-interval-ms`, default 2000ms)
  - Retries up to `app.outbox.max-attempts` (default 5)
