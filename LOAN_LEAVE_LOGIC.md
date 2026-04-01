# Loan and Leave Logic (Deep Implementation Guide)

This document describes the exact implemented behavior in the backend as of the current codebase.

## 1. Access Control and Request Context

### 1.1 Security Layers
Security is enforced in two layers:
- URL-level rules in `SecurityConfig`
- Method-level rules via `@PreAuthorize` on controller methods

Relevant base URL rules:
- `/api/employee/**` -> `EMPLOYEE`, `TEAM_LEADER`, `HR_MANAGER`
- `/api/hr/**` -> `HR_MANAGER`
- `/api/reports/**` -> `EMPLOYEE`, `TEAM_LEADER`, `HR_MANAGER`
- `/public/**` -> public (no auth)

### 1.2 Identity Source
Most business actions use one of these JWT claims:
- `preferred_username` for loading `User` by username
- `sub` (`jwt.getSubject()`) for Keycloak user ID checks and team-leader ownership checks

## 2. Leave Request Domain

Core files:
- `modules/employee/controller/EmployeeLeaveController.java`
- `modules/employee/service/EmployeeLeaveService.java`
- `modules/employee/service/LeaveScoreEngine.java`
- `modules/employee/entity/LeaveRequest.java`
- `modules/employee/repository/LeaveRequestRepository.java`
- `modules/employee/dto/CreateLeaveRequestDto.java`

### 2.1 Leave Enums and Persisted Fields
- `LeaveType`: `ANNUAL`, `SICK`, `UNPAID`, `MATERNITY`, `PATERNITY`, `OTHER`
- `LeaveStatus`: `PENDING`, `APPROVED`, `REJECTED`
- `ApprovalDecision`: `PENDING`, `APPROVED`, `REJECTED`

`LeaveRequest` stores:
- request metadata: user, leave type, start/end dates, numberOfDays, reason, requestDate
- workflow state: `teamLeaderDecision`, `hrDecision`, final `status`
- audit fields: approvalDate, createdAt, updatedAt
- scoring fields: `systemScore`, `systemRecommendation`, `decisionReason`
- document verification: `verificationToken` (unique)
- extra leave metric field: `totalLeaveTakenLast12Months` (nullable in DB, default 0 in getter)

Constructor defaults:
- `teamLeaderDecision = PENDING`
- `hrDecision = PENDING`
- `status = PENDING`
- timestamps initialized to now

### 2.2 Submission Endpoint and Validations
Endpoint:
- `POST /api/employee/leave/request`
- roles: `EMPLOYEE`, `TEAM_LEADER`

DTO (`@Valid`) checks:
- `leaveType` required
- `startDate` required, today or future
- `endDate` required, today or future
- `numberOfDays` required and positive
- `reason` max length 1000

Service-level checks (`validateLeaveRequestDates`):
- `startDate <= endDate`
- `numberOfDays == (endDate - startDate + 1)` in calendar days
- `numberOfDays <= 30`

### 2.3 Team Leader Auto-Bypass Rule
On creation:
- if requester role is `TEAM_LEADER`, service sets `teamLeaderDecision = APPROVED` before scoring/save
- this means TL leave skips TL approval step and awaits HR decision

### 2.4 Leave Scoring Engine (Executed at Submission)
`LeaveScoreEngine.evaluate(leave)` runs immediately at creation.

If critical data missing (`employee`, `start`, `end`):
- score = 50
- recommendation = `REVIEW`
- reason = `Incomplete data — manual review required.`

Otherwise score starts at 100 and applies:

A) Team availability penalty:
- loads full team via `teamRepo.findByIdWithDetails`
- computes team size as `countByTeamId(teamId) + 1` (adds TL)
- counts overlapping leave in team in date range with statuses `PENDING` or `APPROVED`
- compares occupancy percent vs `team.maxLeavePercentage` (default 40)
- penalty:
  - >= maxPct -> 40
  - >= 75% of maxPct -> 20
  - >= 50% of maxPct -> 10
- if no team, check is skipped and reason notes no team assignment

B) Fairness penalty (last 12 months):
- employee approved leave days: `sumLeaveDaysSince(userId, now-12 months)`
- team avg approved leave per member: `avgLeaveDaysPerTeamMember(teamId, since)`
- if team avg > 0, ratio = employeeDays / teamAvg
- penalty:
  - ratio > 2.0 -> 30
  - ratio > 1.5 -> 15
  - ratio > 1.2 -> 5

C) Task impact penalty:
- fetches active overlapping tasks (`status = IN_PROGRESS`) from `TaskRepository.findActiveTasksOverlapping`
- counts priorities:
  - HIGH -> penalty `min(25, highCount*12)`
  - else MEDIUM -> penalty `min(10, mediumCount*5)`

D) Leave type bonus:
- +10 bonus if enum name string contains one of:
  - `SICK`, `MEDICAL`, `EMERGENCY`, `MATERNITY`, `PATERNITY`
- with current leave enum set, practical matches are `SICK`, `MATERNITY`, `PATERNITY`

Final clamp:
- score constrained to `[0..100]`

Recommendation:
- score >= 70 -> `APPROVE`
- 40 <= score < 70 -> `REVIEW`
- score < 40 -> `REJECT`

Important:
- this recommendation is advisory only for leave
- leave submission is not auto-blocked based on score

### 2.5 Read Endpoints
- `GET /api/employee/leave/my-requests`: requester’s own leaves
- `GET /api/employee/leave/pending`: TL only, pending leaves for TL’s team
- `GET /api/employee/leave/my-team`: TL only, all statuses for TL’s team
- `GET /api/employee/leave/all`: HR only, all leaves
- `GET /api/employee/leave/{leaveId}`: returns any leave by ID

Note:
- `getLeaveRequestById` currently has no explicit owner/team filter

### 2.6 Team Leader Decision Flow
Endpoints:
- `POST /api/employee/leave/{leaveId}/team-leader/approve`
- `POST /api/employee/leave/{leaveId}/team-leader/reject`

Checks in service:
- leave must exist
- final leave status must still be `PENDING`
- TL must have a team (`findByTeamLeaderKeycloakId`)
- leave employee must belong to same team as TL

Effects:
- sets `teamLeaderDecision = APPROVED|REJECTED`
- sets `updatedAt = now`
- if rejected:
  - `status = REJECTED`
  - sends rejection email
- if approved:
  - calls `recalculateStatus` (usually remains pending until HR acts)

### 2.7 HR Decision Flow
Endpoints:
- `POST /api/employee/leave/{leaveId}/hr/approve`
- `POST /api/employee/leave/{leaveId}/hr/reject`

Checks in service:
- leave must exist
- if already `REJECTED` -> error
- if already fully `APPROVED` -> error
- TL decision must already be `APPROVED`

Effects:
- sets `hrDecision = APPROVED|REJECTED`
- sets `updatedAt = now`
- if HR rejects:
  - `status = REJECTED`
  - sends rejection email
- if HR approves:
  - calls `recalculateStatus`

### 2.8 Final Status Recalculation
`recalculateStatus` does:
- if TL approved and HR approved:
  - `status = APPROVED`
  - `approvalDate = today`
  - generate UUID `verificationToken` if missing
  - sends approval email
- otherwise no status change (stays pending until both approvals exist)

### 2.9 Leave Email Behavior
Email sender: `HREmailService`

Approval email:
- subject: `Leave Request Approved – LV-xxxxxx`
- includes leave type/date range/day count/reference

Rejection email:
- subject: `Leave Request Not Approved – LV-xxxxxx`
- reason source:
  - `leave.decisionReason` if present
  - otherwise default generic policy message

Operational detail:
- email failures are swallowed in service (`warn` log only), no transaction rollback

### 2.10 Leave PDF and Verification
PDF endpoint:
- `GET /api/reports/leave/{leaveId}`

Authorization logic in `LeaveReportApplicationService`:
- EMPLOYEE: only own PDF
- TEAM_LEADER: own PDF or team members’ PDFs only
- HR_MANAGER: any PDF

Eligibility for leave PDF generation:
- final status must be approved AND TL approved AND HR approved

Public verification endpoint:
- `GET /public/verify/{token}`
- currently checks token only in leave repository (`LeaveRequestRepository.findByVerificationToken`)

## 3. Loan Request Domain

Core files:
- `modules/requests/controller/RequestsController.java`
- `modules/requests/service/RequestsService.java`
- `modules/requests/service/LoanScoreEngine.java`
- `modules/requests/entity/LoanRequest.java`
- `modules/requests/repository/LoanRequestRepository.java`
- `modules/user/entity/Person.java`

### 3.1 Loan Enums and Persisted Fields
- `LoanType`: `PERSONAL_ADVANCE`, `EMERGENCY_LOAN`, `EDUCATION_LOAN`, `MEDICAL_ADVANCE`, `HOUSING_ADVANCE`
- `RequestStatus`: `PENDING`, `APPROVED`, `REJECTED`

`LoanRequest` stores:
- request data: user, type, amount, repaymentMonths, reason
- workflow: status, hrNote, requestedAt, processedAt
- scoring: monthlyInstallment, riskScore, systemRecommendation, decisionReason, meetingRequired
- document verification: unique `verificationToken`

Default on new loan entity:
- `status = PENDING`
- `requestedAt = now`

### 3.2 Loan Submission Endpoint
Endpoint:
- `POST /api/employee/loans`
- roles: `EMPLOYEE`, `TEAM_LEADER`, `HR_MANAGER`

Input handling detail:
- request body is raw `Map<String,Object>`
- amount parsed via `new BigDecimal(body.get("amount").toString())`
- months parsed via `Integer.parseInt(...)`

No bean-validation DTO is used here.

### 3.3 Hard Eligibility Rules (`validateLoanEligibility`)
Rules that block before save:
1. user must have `person`
2. salary must exist and be > 0
3. requested amount must be <= `3 * salary`
4. user must not already have any loan with status `PENDING` or `APPROVED`
5. if hireDate exists, tenure must be >= 6 months

Behavioral detail:
- if hireDate is null, rule #5 is not enforced (treated as pass)

### 3.4 Loan Scoring Engine (After Eligibility)
Engine always sets installment and risk context.

Step 1:
- monthly installment = `amount / repaymentMonths` with scale 2, HALF_UP

Step 2: score starts at 100

A) Loan ratio penalty vs max (`3*salary`):
- ratio > 0.9 -> 40
- ratio > 0.7 -> 25
- ratio > 0.5 -> 10

B) Deduction penalty using `(existingMonthlyDeductions + installment) / salary`:
- > 40% -> 30
- > 30% -> 20
- > 20% -> 8

C) Stability bonus by tenure:
- >= 36 months -> +20
- >= 24 months -> +15
- >= 12 months -> +10
- else (if hireDate exists) -> +5

D) History penalty from prior loan outcomes:
- counts previous loans where status is APPROVED or REJECTED
- >= 3 -> 10
- >= 2 -> 5

Final score:
- clamped minimum at 0

Recommendation logic:
- if deduction ratio > 40%:
  - recommendation = `REJECT`
  - reason includes `AUTO-REJECTED: monthly deductions would exceed 40% of salary.`
  - meetingRequired = false
- else if score >= 65:
  - recommendation = `APPROVE`, meetingRequired=false
- else if score >= 35:
  - recommendation = `REVIEW`, meetingRequired=true
- else:
  - recommendation = `REJECT`, meetingRequired=false

### 3.5 Auto-Block on Specific Rejection
After scoring in `createLoanRequest`:
- if recommendation is `REJECT` AND decisionReason contains `AUTO-REJECTED`, service throws `BadRequestException`
- request is not persisted in that case

Other `REJECT` outcomes without `AUTO-REJECTED` are still persisted as pending requests for HR decision.

### 3.6 Loan Eligibility Preview Endpoint
Endpoint:
- `GET /api/employee/loans/eligibility`

Returns:
- `eligible`, `reason`, `salary`, `maxLoan`, `monthsEmployed`, `hasActiveLoan`, `hireDate`

Computation:
- salary missing -> immediately not eligible
- hasActiveLoan true if any pending or approved loan exists
- seniority OK if hireDate null OR tenure >= 6 months
- eligible = `!hasActiveLoan && seniorityOk`

### 3.7 HR Loan Decision Flow
Endpoints:
- `GET /api/hr/loans`
- `POST /api/hr/loans/{id}/approve`
- `POST /api/hr/loans/{id}/reject`

Decision constraints:
- only `PENDING` loans can be decided

Approve effects:
- status -> `APPROVED`
- `processedAt = now`
- generate UUID verification token
- if installment exists and person exists:
  - `person.currentMonthlyDeductions += monthlyInstallment`
  - person saved via `personRepository`

Reject effects:
- status -> `REJECTED`
- `processedAt = now`

Email on decision:
- always called after save
- approval email includes amount/months/monthly installment
- rejection reason uses:
  - `hrNote` if provided and non-blank
  - else loan `decisionReason`

Operational detail:
- loan email send errors are swallowed; main flow continues

### 3.8 Loan Read and Report Endpoints
Employee view:
- `GET /api/employee/loans`

HR view:
- `GET /api/hr/loans`

Loan PDF:
- `GET /api/reports/loans/{id}`
- service requires request status `APPROVED`
- ownership checker currently uses a helper with an HR detection bug (see section 5)

## 4. Shared Exception and Response Behavior

`GlobalExceptionHandler` maps custom exceptions:
- `ResourceNotFoundException` -> 404
- `BadRequestException` -> 400
- `UnauthorizedException` -> 403
- `AuthenticationException` -> 401
- `AccessDeniedException` -> 403

Error body shape:
- `timestamp`, `status`, `error`, `message`

## 5. Important Subtle Behaviors and Edge Cases in Current Code

1. Leave single-item fetch does not enforce owner/team restriction:
- `GET /api/employee/leave/{leaveId}` returns by ID for any allowed role.

2. Public verify endpoint validates leave tokens only:
- QR tokens for loan/document/auth are generated too, but `/public/verify/{token}` checks only leave table.

3. RequestsService PDF owner/HR check bug:
- methods `getDocumentRequestForPdf`, `getLoanRequestForPdf`, `getAuthRequestForPdf`
- helper `validateOwnerOrHR(ownerKeycloakId, requesterKeycloakId, isHR)` is called with `isHR` derived from owner role, not requester role
- this can incorrectly deny HR_MANAGER downloading others’ approved request PDFs.

4. Leave rejection reason source is not explicit user input:
- leave reject endpoints do not accept `hrNote`/`decisionNote`
- rejection email reason comes from scoring `decisionReason` or default text.

5. Loan API input robustness depends on runtime parsing:
- because raw map is used, malformed types can throw generic errors before business validation.

## 6. Data and Query Details Used by Logic

Leave-related repository logic:
- team overlap count includes `PENDING` and `APPROVED` statuses
- fairness uses only `APPROVED` leave records
- overlap condition uses inclusive date boundaries (`start_date <= endDate AND end_date >= startDate`)

Task overlap logic in leave scoring:
- only `IN_PROGRESS` tasks counted
- overlap query handles tasks with null `start_date` by checking due date against leave start

Loan active-loan check:
- considers both `PENDING` and `APPROVED` as active for eligibility blocking
- previous loan history penalty uses only `APPROVED` and `REJECTED`

## 7. Quick Endpoint Matrix (Leave + Loan)

Leave:
- `POST /api/employee/leave/request`
- `GET /api/employee/leave/my-requests`
- `GET /api/employee/leave/{leaveId}`
- `GET /api/employee/leave/pending` (TL)
- `GET /api/employee/leave/my-team` (TL)
- `GET /api/employee/leave/all` (HR)
- `POST /api/employee/leave/{leaveId}/team-leader/approve` (TL)
- `POST /api/employee/leave/{leaveId}/team-leader/reject` (TL)
- `POST /api/employee/leave/{leaveId}/hr/approve` (HR)
- `POST /api/employee/leave/{leaveId}/hr/reject` (HR)

Loan:
- `POST /api/employee/loans`
- `GET /api/employee/loans`
- `GET /api/employee/loans/eligibility`
- `GET /api/hr/loans` (HR)
- `POST /api/hr/loans/{id}/approve` (HR)
- `POST /api/hr/loans/{id}/reject` (HR)
- `GET /api/reports/loans/{id}`

Public verification:
- `GET /public/verify/{token}` (currently leave-token validation only)
