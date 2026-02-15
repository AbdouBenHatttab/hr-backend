# HR Management System - API Testing Guide

## Base Configuration
- **Base URL**: `http://localhost:8081`
- **Keycloak URL**: `http://localhost:8080`
- **Date Format**: `yyyy-MM-dd` (e.g., "1990-05-15")

## Important Notes
- **birthDate format**: Must be in "yyyy-MM-dd" format (e.g., "1990-05-15")
- **birthDate is OPTIONAL**: If not provided, will be stored as NULL
- **Recommendation**: Always provide birthDate during registration for new users
- New users start with role `NEW_USER` and cannot access protected endpoints
- HR Manager must assign `EMPLOYEE` or `TEAM_LEADER` role in Keycloak Admin Console
- Tokens expire after 15 minutes (900 seconds)

---

## 1. EMPLOYEE REGISTRATION & LOGIN TESTS

### Test 1.1: Register Employee 1 (Jane Smith)
**Endpoint**: `POST /public/auth/register`  
**Body** (raw JSON):
```json
{
    "username": "jane_smith",
    "password": "SecurePassword123",
    "firstName": "Jane",
    "lastName": "Smith",
    "email": "jane.smith@company.com",
    "phone": "+1-555-0123",
    "birthDate": "1990-05-15",
    "address": "123 Main Street, New York, NY 10001",
    "maritalStatus": "Single",
    "numberOfChildren": 0
}
```

**Expected Response**:
```json
{
    "success": true,
    "message": "User registered successfully"
}
```

---

### Test 1.2: Register Employee 2 (Sarah Johnson)
**Endpoint**: `POST /public/auth/register`  
**Body** (raw JSON):
```json
{
    "username": "sarah_employee",
    "password": "Employee123456",
    "firstName": "Sarah",
    "lastName": "Johnson",
    "email": "sarah.johnson@company.com",
    "phone": "+216-99-111-222",
    "birthDate": "1985-03-20",
    "address": "456 Oak Ave, Office Building",
    "maritalStatus": "married",
    "numberOfChildren": 3
}
```

**Expected Response**:
```json
{
    "success": true,
    "message": "User registered successfully"
}
```

---

### Test 1.3: Login Employee 1
**Endpoint**: `POST /public/auth/login`  
**Body** (raw JSON):
```json
{
    "username": "jane_smith",
    "password": "SecurePassword123"
}
```

**Expected Response**:
```json
{
    "success": true,
    "message": "Login successful",
    "username": "jane_smith",
    "email": "jane.smith@company.com",
    "roles": ["EMPLOYEE"],
    "accessToken": "eyJhbGc...",
    "refreshToken": "eyJhbGc...",
    "tokenType": "Bearer",
    "expiresIn": 900
}
```

**Note**: Save the `accessToken` - you'll need it for authenticated requests!

---

### Test 1.4: Employee Dashboard Access
**Endpoint**: `GET /api/employee/info`  
**Authorization**: Bearer Token (use accessToken from login)  
**Headers**:
- `Authorization: Bearer {accessToken}`

**Expected Response**:
```json
{
    "message": "Hello Employee!",
    "username": "jane_smith",
    "email": "jane.smith@company.com",
    "role": "EMPLOYEE"
}
```

---

## 2. TEAM LEADER REGISTRATION & LOGIN TESTS

### Test 2.1: Register Team Leader (Mike Wilson)
**Endpoint**: `POST /public/auth/register`  
**Body** (raw JSON):
```json
{
    "username": "mike_leader",
    "password": "Leader123456",
    "firstName": "Mike",
    "lastName": "Wilson",
    "email": "mike.wilson@company.com",
    "phone": "+216-97-333-444",
    "birthDate": "1982-11-30",
    "address": "789 Pine Rd, Leadership Floor",
    "maritalStatus": "Married",
    "numberOfChildren": 1
}
```

---

### Test 2.2: Login Team Leader
**Endpoint**: `POST /public/auth/login`  
**Body** (raw JSON):
```json
{
    "username": "mike_leader",
    "password": "Leader123456"
}
```

---

### Test 2.3: Team Leader Dashboard Access
**Endpoint**: `GET /api/leader/info`  
**Authorization**: Bearer Token  
**Headers**:
- `Authorization: Bearer {accessToken}`

**Expected Response**:
```json
{
    "message": "Hello Team Leader!",
    "username": "mike_leader",
    "email": "mike.wilson@company.com",
    "role": "TEAM_LEADER"
}
```

---

## 3. HR MANAGER REGISTRATION & TESTS

### Test 3.1: Register HR Manager
**Endpoint**: `POST /public/auth/register`  
**Body** (raw JSON):
```json
{
    "username": "hr_manager",
    "password": "HRManager123",
    "firstName": "HR",
    "lastName": "Manager",
    "email": "hr@company.com",
    "phone": "+216-98-765-432",
    "birthDate": "1975-07-15",
    "address": "HR Department, Company HQ",
    "maritalStatus": "Married",
    "numberOfChildren": 1
}
```

**Important**: After registration, go to Keycloak Admin Console and assign `HR_MANAGER` role manually!

---

### Test 3.2: Login HR Manager
**Endpoint**: `POST /public/auth/login`  
**Body** (raw JSON):
```json
{
    "username": "hr_manager",
    "password": "HRManager123"
}
```

---

### Test 3.3: HR Manager Dashboard
**Endpoint**: `GET /api/hr/dashboard`  
**Authorization**: Bearer Token  

**Expected Response**:
```json
{
    "message": "Welcome to HR Dashboard",
    "username": "hr_manager",
    "email": "hr@company.com",
    "role": "HR_MANAGER"
}
```

---

### Test 3.4: Get All Users (HR Manager Only)
**Endpoint**: `GET /api/hr/users`  
**Authorization**: Bearer Token (HR Manager)

**Expected Response**:
```json
{
    "message": "All users retrieved successfully",
    "requestedBy": "hr_manager",
    "totalCount": 5,
    "users": [
        {
            "id": 2,
            "username": "jane_smith",
            "email": "jane.smith@company.com",
            "role": "EMPLOYEE",
            "active": true,
            "emailVerified": true,
            "registrationDate": "2026-02-09",
            "keycloakId": "bd6431f9-...",
            "personalInfo": {
                "firstName": "Jane",
                "lastName": "Smith",
                "email": "jane.smith@company.com",
                "phone": "+1-555-0123",
                "birthDate": "1990-05-15",
                "address": "123 Main Street, New York, NY 10001",
                "maritalStatus": "Single",
                "numberOfChildren": 0
            }
        }
    ]
}
```

---

### Test 3.5: Get Pending Approvals (Users with NEW_USER role)
**Endpoint**: `GET /api/hr/pending-approvals`  
**Authorization**: Bearer Token (HR Manager)

**Expected Response**:
```json
{
    "message": "Pending role assignments",
    "requestedBy": "hr_manager",
    "totalCount": 2,
    "pendingUsers": [
        {
            "id": 4,
            "username": "jane_smith2",
            "email": "jane.smith2@company.com",
            "fullName": "Jane Smith",
            "role": "NEW_USER",
            "registrationDate": "2026-02-10"
        }
    ],
    "instructions": "Go to Keycloak Admin → Users → Select User → Role mapping → Assign role (EMPLOYEE/TEAM_LEADER)",
    "note": "HR Manager can assign roles to these users in Keycloak Admin Console"
}
```

---

### Test 3.6: Get Login History
**Endpoint**: `GET /api/hr/login-history`  
**Authorization**: Bearer Token (HR Manager)

**Expected Response**:
```json
{
    "message": "Login history retrieved successfully",
    "requestedBy": "hr_manager",
    "totalCount": 15,
    "loginHistory": [
        {
            "id": 1,
            "loginDate": "2026-02-10T14:30:00",
            "userId": 2,
            "username": "jane_smith"
        },
        {
            "id": 2,
            "loginDate": "2026-02-10T15:45:23",
            "userId": 3,
            "username": "hr_manager"
        },
        {
            "id": 3,
            "loginDate": "2026-02-10T16:20:10",
            "userId": 5,
            "username": "sarah_employee"
        }
    ]
}
```

**Note**: Only essential information is returned:
- `id`: Login history record ID
- `loginDate`: Date and time of login (ISO 8601 format)
- `userId`: The user's database ID
- `username`: The username who logged in

---

## 4. ASSIGNING ROLES IN KEYCLOAK

### Manual Process (Required for NEW_USER → EMPLOYEE/TEAM_LEADER)

1. **Access Keycloak Admin Console**:
   - URL: `http://localhost:8080`
   - Username: `admin`
   - Password: Your Keycloak admin password

2. **Navigate to Users**:
   - Select realm: `hr-realm`
   - Click on "Users" in the left menu
   - Search for the username (e.g., "jane_smith")

3. **Assign Role**:
   - Click on the user
   - Go to "Role mapping" tab
   - Click "Assign role"
   - Select the role: `EMPLOYEE` or `TEAM_LEADER`
   - Click "Assign"

4. **Test Access**:
   - User must logout and login again to get new role
   - New token will include the assigned role

---

## 5. ERROR SCENARIOS

### Test 5.1: Registration with Duplicate Email
**Expected Response** (400 Bad Request):
```json
{
    "success": false,
    "message": "This email is already registered. Please use a different email or try logging in."
}
```

---

### Test 5.2: Registration with Duplicate Username
**Expected Response** (400 Bad Request):
```json
{
    "success": false,
    "message": "This username is already taken. Please choose a different username."
}
```

---

### Test 5.3: Invalid Credentials
**Endpoint**: `POST /public/auth/login`  
**Body**:
```json
{
    "username": "jane_smith",
    "password": "WrongPassword"
}
```

**Expected Response** (400 Bad Request):
```json
{
    "success": false,
    "message": "Invalid username or password",
    "accessToken": null,
    "refreshToken": null
}
```

---

### Test 5.4: NEW_USER Trying to Access Protected Endpoint
**Endpoint**: `GET /api/employee/info`  
**Authorization**: Bearer Token (NEW_USER)

**Expected Response** (403 Forbidden):
```json
{
    "timestamp": "2026-02-10T16:27:54.129",
    "status": 403,
    "error": "Forbidden",
    "message": "Access Denied"
}
```

---

### Test 5.5: Invalid birthDate Format
**Endpoint**: `POST /public/auth/register`  
**Body**:
```json
{
    "username": "test_user",
    "password": "Test123456",
    "firstName": "Test",
    "lastName": "User",
    "email": "test@company.com",
    "birthDate": "15-05-1990",
    "maritalStatus": "Single",
    "numberOfChildren": 0
}
```

**Expected Response** (400 Bad Request):
```json
{
    "success": false,
    "message": "Invalid date format. Please use yyyy-MM-dd format (e.g., 1990-05-15)"
}
```

---

## 6. ROLE-BASED ACCESS SUMMARY

| Endpoint | NEW_USER | EMPLOYEE | TEAM_LEADER | HR_MANAGER |
|----------|----------|----------|-------------|------------|
| `POST /public/auth/register` | ✅ | ✅ | ✅ | ✅ |
| `POST /public/auth/login` | ✅ | ✅ | ✅ | ✅ |
| `GET /api/employee/info` | ❌ | ✅ | ❌ | ❌ |
| `GET /api/leader/info` | ❌ | ❌ | ✅ | ❌ |
| `GET /api/hr/dashboard` | ❌ | ❌ | ❌ | ✅ |
| `GET /api/hr/users` | ❌ | ❌ | ❌ | ✅ |
| `GET /api/hr/pending-approvals` | ❌ | ❌ | ❌ | ✅ |
| `GET /api/hr/login-history` | ❌ | ❌ | ❌ | ✅ |

---

## 7. POSTMAN COLLECTION VARIABLES

Set these variables in Postman for easier testing:

- `base_url`: `http://localhost:8081`
- `token_hr`: (Set after HR Manager login)
- `employee_token`: (Set after Employee login)
- `team_leader_token`: (Set after Team Leader login)

---

## 8. TROUBLESHOOTING

### Issue: IP address and browser still showing in pgAdmin
**Solution**: The application no longer stores IP address and browser information. To remove these columns from existing database:

1. Open pgAdmin and connect to `hr_db` database
2. Run the SQL script `cleanup_login_history.sql` located in the project root
3. Or manually run these commands:
```sql
ALTER TABLE login_history DROP COLUMN IF EXISTS ip_address;
ALTER TABLE login_history DROP COLUMN IF EXISTS browser;
```

### Issue: birthDate shows as NULL in database
**Solution**: Ensure birthDate is provided in "yyyy-MM-dd" format during registration.

**Example**:
```json
{
    "birthDate": "1990-05-15"  ✅ CORRECT
}
```

**NOT**:
```json
{
    "birthDate": "05/15/1990"  ❌ WRONG
}
```

### Issue: User can't access endpoints after role assignment
**Solution**: User must logout and login again to get a new token with updated roles.

### Issue: 403 Forbidden even with valid token
**Solution**: Check if the role in Keycloak matches the endpoint requirement:
- Employee endpoints require `EMPLOYEE` role
- Team Leader endpoints require `TEAM_LEADER` role
- HR endpoints require `HR_MANAGER` role

### Issue: Token expired
**Solution**: Login again to get a fresh token. Tokens expire after 15 minutes (900 seconds).

---

## 9. NEXT STEPS AFTER TESTING

1. ✅ Register all test users
2. ✅ Assign appropriate roles in Keycloak
3. ✅ Test role-based access control
4. ✅ Verify birthDate is stored correctly
5. ✅ Test login history tracking
6. 🔄 Implement role assignment API (future enhancement)
7. 🔄 Add user profile update functionality
8. 🔄 Add password reset functionality

---

**Last Updated**: February 15, 2026

