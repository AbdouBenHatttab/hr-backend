# Employee Leave Request Module

This module handles the complete leave request workflow for employees, including submission, approval, and PDF generation.

## Module Structure

```
modules/employee/
├── controller/
│   └── EmployeeLeaveController.java
├── service/
│   └── EmployeeLeaveService.java
├── dto/
│   ├── CreateLeaveRequestDto.java
│   └── LeaveRequestResponseDto.java
├── entity/
│   └── LeaveRequest.java
└── repository/
    └── LeaveRequestRepository.java
```

## Endpoints

### 1. Submit Leave Request
```http
POST /api/employee/leave/request
Authorization: Bearer <EMPLOYEE_TOKEN>
Content-Type: application/json

{
  "leaveType": "ANNUAL",
  "startDate": "2026-04-01",
  "endDate": "2026-04-05",
  "numberOfDays": 5,
  "reason": "Family vacation"
}
```

**Response:**
```json
{
  "success": true,
  "message": "Leave request submitted successfully",
  "data": {
    "id": 1,
    "employeeFullName": "Diana Prince",
    "employeeEmail": "diana.prince@company.com",
    "leaveType": "ANNUAL",
    "startDate": "2026-04-01",
    "endDate": "2026-04-05",
    "numberOfDays": 5,
    "reason": "Family vacation",
    "status": "PENDING",
    "teamLeaderDecision": "PENDING",
    "hrDecision": "PENDING"
  }
}
```

### 2. Get My Leave Requests
```http
GET /api/employee/leave/my-requests
Authorization: Bearer <EMPLOYEE_TOKEN>
```

**Response:**
```json
{
  "success": true,
  "message": "Your leave requests retrieved successfully",
  "count": 1,
  "data": [...]
}
```

### 3. Get Pending Leave Requests (for Managers)
```http
GET /api/employee/leave/pending
Authorization: Bearer <TEAM_LEADER_TOKEN> or <HR_MANAGER_TOKEN>
```

**Response:**
```json
{
  "success": true,
  "message": "Pending leave requests retrieved successfully",
  "count": 3,
  "data": [...]
}
```

### 4. Get Specific Leave Request
```http
GET /api/employee/leave/{leaveId}
Authorization: Bearer <EMPLOYEE_TOKEN>
```

## Leave Request Workflow

### Status: PENDING
- Employee submits leave request
- `teamLeaderDecision` = PENDING
- `hrDecision` = PENDING
- `status` = PENDING

### Status: APPROVED
- Team Leader approves: `teamLeaderDecision` = APPROVED
- HR Manager approves: `hrDecision` = APPROVED
- `status` = APPROVED
- PDF can be generated

### Status: REJECTED
- If either Team Leader or HR Manager rejects
- `status` = REJECTED
- No PDF generation allowed

## Validation Rules

1. **Start Date & End Date:**
   - Must be today or in the future
   - Start date must be ≤ end date

2. **Number of Days:**
   - Must match the date range
   - Maximum 30 days per request

3. **Leave Type:**
   - ANNUAL
   - SICK
   - UNPAID
   - MATERNITY
   - PATERNITY
   - OTHER

## Database Schema

```sql
CREATE TABLE leave_requests (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    leave_type VARCHAR(50) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    number_of_days INT NOT NULL,
    reason VARCHAR(1000),
    request_date TIMESTAMP NOT NULL,
    team_leader_decision VARCHAR(50) NOT NULL,
    hr_decision VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    approval_date DATE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id)
);
```

## PDF Generation

Once a leave request is fully approved (both Team Leader and HR Manager approve):

```http
GET /api/reports/leave/{leaveId}
Authorization: Bearer <EMPLOYEE_TOKEN> or <TEAM_LEADER_TOKEN> or <HR_MANAGER_TOKEN>
```

**Response:**
- PDF file download with filename: `leave_approval_{employeeName}_{startDate}.pdf`

## Integration with Other Modules

- **User Module:** Links leave requests to employee accounts
- **Report Module:** Generates PDF approvals
- **HR Module:** Final approval by HR Manager
- **Authentication:** Secured with role-based access control

## Future Enhancements

1. **Leave Balance Tracking:**
   - Track employee annual leave balance
   - Validate against remaining balance

2. **Approval Workflow:**
   - Add rejection reasons
   - Add comments field for approvers

3. **Notifications:**
   - Email notifications on submission
   - Email notifications on approval/rejection

4. **Reports:**
   - Leave usage statistics
   - Team leave calendar

