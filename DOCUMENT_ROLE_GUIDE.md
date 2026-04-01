# Document Roles Guide (Backend)

This file explains how document-related authorization works in the backend.

## 1) System Roles

Defined in `TypeRole`:

- `NEW_USER`
- `EMPLOYEE`
- `TEAM_LEADER`
- `HR_MANAGER`

Source: `src/main/java/tn/isetbizerte/pfe/hrbackend/common/enums/TypeRole.java`

## 2) How Roles Are Loaded

JWT roles are converted to Spring authorities as `ROLE_<ROLE_NAME>` by `KeycloakRoleConverter`.

- Example: `HR_MANAGER` -> `ROLE_HR_MANAGER`
- If JWT has no roles and DB fallback fails, backend defaults to `NEW_USER`.

Sources:

- `src/main/java/tn/isetbizerte/pfe/hrbackend/security/KeycloakRoleConverter.java`
- `src/main/java/tn/isetbizerte/pfe/hrbackend/security/SecurityConfig.java`

## 3) Route-Level Security (Global)

From `SecurityConfig`:

- `/api/new-user/**` -> `NEW_USER`
- `/api/me/**` -> `EMPLOYEE` or `TEAM_LEADER` or `HR_MANAGER`
- `/api/employee/**` -> `EMPLOYEE` or `TEAM_LEADER` or `HR_MANAGER`
- `/api/hr/**` -> `HR_MANAGER`
- `/api/reports/**` -> `EMPLOYEE` or `TEAM_LEADER` or `HR_MANAGER`
- `/public/**` -> public (no auth)

Source: `src/main/java/tn/isetbizerte/pfe/hrbackend/security/SecurityConfig.java`

## 4) Document Endpoints and Required Roles

Defined in `RequestsController`:

- `POST /api/employee/documents` -> `EMPLOYEE`, `TEAM_LEADER`, `HR_MANAGER`
- `GET /api/employee/documents` -> `EMPLOYEE`, `TEAM_LEADER`, `HR_MANAGER`
- `GET /api/hr/documents` -> `HR_MANAGER`
- `POST /api/hr/documents/{id}/approve` -> `HR_MANAGER`
- `POST /api/hr/documents/{id}/reject` -> `HR_MANAGER`

Source: `src/main/java/tn/isetbizerte/pfe/hrbackend/modules/requests/controller/RequestsController.java`

## 5) Document PDF Download Roles

Defined in `RequestReportController`:

- `GET /api/reports/documents/{id}` -> `EMPLOYEE`, `TEAM_LEADER`, `HR_MANAGER`

Source: `src/main/java/tn/isetbizerte/pfe/hrbackend/modules/requests/controller/RequestReportController.java`

Service-level rule in `RequestsService`:

- PDF is only available when request status is `APPROVED`.
- Then backend checks requester ownership/HR rule before allowing download.

Source: `src/main/java/tn/isetbizerte/pfe/hrbackend/modules/requests/service/RequestsService.java`

## 6) Public Verification Endpoint

No auth required:

- `GET /public/verify/{token}`

This is used by QR verification for issued documents.

Source: `src/main/java/tn/isetbizerte/pfe/hrbackend/modules/report/controller/VerifyController.java`

## 7) Document Request Lifecycle

`DocumentRequest` fields use:

- Status enum: `PENDING`, `APPROVED`, `REJECTED`
- On approve: backend sets `verificationToken`
- On approve/reject: backend sets `processedAt` and optional `hrNote`

Sources:

- `src/main/java/tn/isetbizerte/pfe/hrbackend/common/enums/RequestStatus.java`
- `src/main/java/tn/isetbizerte/pfe/hrbackend/modules/requests/entity/DocumentRequest.java`
- `src/main/java/tn/isetbizerte/pfe/hrbackend/modules/requests/service/RequestsService.java`

## 8) Supported Document Types

Defined in `DocumentType`:

- `EMPLOYMENT_CERTIFICATE`
- `SALARY_CERTIFICATE`
- `WORK_REFERENCE_LETTER`
- `LEAVE_BALANCE_STATEMENT`
- `CONTRACT_COPY`
- `EXPERIENCE_CERTIFICATE`

Source: `src/main/java/tn/isetbizerte/pfe/hrbackend/common/enums/DocumentType.java`

