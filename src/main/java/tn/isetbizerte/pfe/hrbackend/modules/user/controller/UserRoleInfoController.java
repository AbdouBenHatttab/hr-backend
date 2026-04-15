package tn.isetbizerte.pfe.hrbackend.modules.user.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.Person;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.PersonRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.UserRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
public class UserRoleInfoController {

    private final UserRepository   userRepository;
    private final PersonRepository  personRepository;

    public UserRoleInfoController(UserRepository userRepository, PersonRepository personRepository) {
        this.userRepository  = userRepository;
        this.personRepository = personRepository;
    }

    /**
     * GET /api/me — returns the current user's full profile from the database.
     * Accessible by EMPLOYEE, TEAM_LEADER, and HR_MANAGER.
     * Replaces the old pattern of calling /api/hr/users and filtering by username.
     */
    @PreAuthorize("hasAnyRole('EMPLOYEE','TEAM_LEADER','HR_MANAGER')")
    @GetMapping("/api/me")
    public Map<String, Object> getMyProfile(@AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        Optional<User> userOpt = userRepository.findByUsername(username);

        Map<String, Object> response = new HashMap<>();
        if (userOpt.isEmpty()) {
            response.put("success", false);
            response.put("message", "Profile not found");
            return response;
        }

        User user = userOpt.get();
        response.put("success", true);
        response.put("id", user.getId());
        response.put("username", user.getUsername());
        response.put("role", user.getRole());
        response.put("active", user.getActive());
        response.put("emailVerified", user.getEmailVerified());
        response.put("registrationDate", user.getRegistrationDate());

        if (user.getPerson() != null) {
            Person p = user.getPerson();
            Map<String, Object> personalInfo = new HashMap<>();
            personalInfo.put("firstName",        p.getFirstName());
            personalInfo.put("lastName",         p.getLastName());
            personalInfo.put("email",            p.getEmail()          != null ? p.getEmail()           : "");
            personalInfo.put("phone",            p.getPhone()          != null ? p.getPhone()           : "");
            personalInfo.put("address",          p.getAddress()        != null ? p.getAddress()         : "");
            personalInfo.put("birthDate",        p.getBirthDate()      != null ? p.getBirthDate().toString() : "");
            personalInfo.put("maritalStatus",    p.getMaritalStatus()  != null ? p.getMaritalStatus()   : "");
            personalInfo.put("numberOfChildren", p.getNumberOfChildren());
            personalInfo.put("avatarPhoto",      p.getAvatarPhoto()    != null ? p.getAvatarPhoto()     : "");
            personalInfo.put("avatarColor",      p.getAvatarColor()    != null ? p.getAvatarColor()     : "");
            personalInfo.put("department",       p.getDepartment()     != null ? p.getDepartment()      : "");
            personalInfo.put("jobTitle",         p.getJobTitle()       != null ? p.getJobTitle()        : "");
            personalInfo.put("salary",          p.getSalary()         != null ? p.getSalary()           : null);
            personalInfo.put("hireDate",         p.getHireDate()       != null ? p.getHireDate().toString() : "");
            response.put("personalInfo", personalInfo);
        }

        return response;
    }

    /**
     * PATCH /api/me — update own editable profile fields.
     * Only phone, address, maritalStatus, numberOfChildren can be changed.
     * firstName, lastName, email, username are immutable after registration.
     */
    @PreAuthorize("hasAnyRole('EMPLOYEE','TEAM_LEADER','HR_MANAGER')")
    @PatchMapping("/api/me")
    public Map<String, Object> updateMyProfile(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Jwt jwt) {

        String username = jwt.getClaimAsString("preferred_username");
        Optional<User> userOpt = userRepository.findByUsername(username);

        Map<String, Object> response = new HashMap<>();
        if (userOpt.isEmpty() || userOpt.get().getPerson() == null) {
            response.put("success", false);
            response.put("message", "Profile not found");
            return response;
        }

        Person p = userOpt.get().getPerson();

        if (body.containsKey("phone"))           p.setPhone((String) body.get("phone"));
        if (body.containsKey("address"))         p.setAddress((String) body.get("address"));
        if (body.containsKey("maritalStatus"))   p.setMaritalStatus((String) body.get("maritalStatus"));
        if (body.containsKey("numberOfChildren")) {
            Object val = body.get("numberOfChildren");
            p.setNumberOfChildren(val instanceof Integer ? (Integer) val : Integer.parseInt(val.toString()));
        }
        if (body.containsKey("avatarPhoto"))  p.setAvatarPhoto((String) body.get("avatarPhoto"));
        if (body.containsKey("avatarColor"))   p.setAvatarColor((String) body.get("avatarColor"));
        if (body.containsKey("department"))    p.setDepartment((String) body.get("department"));

        personRepository.save(p);

        response.put("success", true);
        response.put("message", "Profile updated successfully");
        return response;
    }

    /**
     * PATCH /api/hr/users/{userId}/employment
     * HR sets salary and hire date for any user. Employees cannot self-edit salary.
     */
    @PreAuthorize("hasRole('HR_MANAGER')")
    @PatchMapping("/api/hr/users/{userId}/employment")
    public Map<String, Object> updateEmployment(
            @PathVariable Long userId,
            @RequestBody Map<String, Object> body) {

        Optional<User> userOpt = userRepository.findById(userId);
        Map<String, Object> response = new HashMap<>();
        if (userOpt.isEmpty() || userOpt.get().getPerson() == null) {
            response.put("success", false);
            response.put("message", "User not found");
            return response;
        }
        Person p = userOpt.get().getPerson();
        if (body.containsKey("salary") && body.get("salary") != null) {
            p.setSalary(new java.math.BigDecimal(body.get("salary").toString()));
        }
        if (body.containsKey("hireDate") && body.get("hireDate") != null
                && !body.get("hireDate").toString().isEmpty()) {
            p.setHireDate(java.time.LocalDate.parse(body.get("hireDate").toString()));
        }
        if (body.containsKey("department")) p.setDepartment((String) body.get("department"));
        if (body.containsKey("jobTitle")) p.setJobTitle((String) body.get("jobTitle"));
        personRepository.save(p);
        response.put("success", true);
        response.put("message", "Employment details updated.");
        return response;
    }

    @GetMapping("/api/new-user/waiting")
    public Map<String, Object> newUserWaiting(@AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Your account is pending approval. Please wait for HR Manager to assign you a role.");
        response.put("status", "WAITING_FOR_APPROVAL");
        response.put("username", jwt.getClaimAsString("preferred_username"));
        response.put("role", extractPrimaryRole(jwt));
        return response;
    }

    @GetMapping("/api/employee/info")
    public Map<String, Object> employeeInfo(@AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> response = new HashMap<>();
        response.put("username", jwt.getClaimAsString("preferred_username"));
        response.put("role", extractPrimaryRole(jwt));
        return response;
    }

    @GetMapping("/api/leader/info")
    public Map<String, Object> leaderInfo(@AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> response = new HashMap<>();
        response.put("username", jwt.getClaimAsString("preferred_username"));
        response.put("role", extractPrimaryRole(jwt));
        return response;
    }

    @SuppressWarnings("unchecked")
    private String extractPrimaryRole(Jwt jwt) {
        Map<String, Object> realmAccess = (Map<String, Object>) jwt.getClaim("realm_access");
        if (realmAccess == null || realmAccess.get("roles") == null) return "NEW_USER";
        List<String> roles = (List<String>) realmAccess.get("roles");
        for (String role : new String[]{"HR_MANAGER", "TEAM_LEADER", "EMPLOYEE", "NEW_USER"}) {
            if (roles.contains(role)) return role;
        }
        return "NEW_USER";
    }
}
