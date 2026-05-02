package tn.isetbizerte.pfe.hrbackend.modules.user.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import tn.isetbizerte.pfe.hrbackend.common.exception.BadRequestException;
import tn.isetbizerte.pfe.hrbackend.common.exception.ResourceNotFoundException;
import tn.isetbizerte.pfe.hrbackend.modules.department.service.DepartmentService;
import tn.isetbizerte.pfe.hrbackend.modules.jobtitle.service.JobTitleService;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.Person;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.dto.UpdateEmploymentRequest;
import tn.isetbizerte.pfe.hrbackend.modules.user.dto.UpdateMyProfileRequest;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.PersonRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.UserRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.service.AuthenticatedUserResolver;
import tn.isetbizerte.pfe.hrbackend.modules.user.service.EmploymentSalaryService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
public class UserRoleInfoController {

    private final UserRepository   userRepository;
    private final PersonRepository  personRepository;
    private final DepartmentService departmentService;
    private final JobTitleService jobTitleService;
    private final EmploymentSalaryService employmentSalaryService;
    private final AuthenticatedUserResolver authenticatedUserResolver;
    public UserRoleInfoController(
            UserRepository userRepository,
            PersonRepository personRepository,
            DepartmentService departmentService,
            JobTitleService jobTitleService,
            EmploymentSalaryService employmentSalaryService,
            AuthenticatedUserResolver authenticatedUserResolver) {
        this.userRepository  = userRepository;
        this.personRepository = personRepository;
        this.departmentService = departmentService;
        this.jobTitleService = jobTitleService;
        this.employmentSalaryService = employmentSalaryService;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    /**
     * GET /api/me — returns the current user's full profile from the database.
     * Accessible by EMPLOYEE, TEAM_LEADER, and HR_MANAGER.
     * Replaces the old pattern of calling /api/hr/users and filtering by username.
     */
    @PreAuthorize("hasAnyRole('EMPLOYEE','TEAM_LEADER','HR_MANAGER')")
    @GetMapping("/api/me")
    public Map<String, Object> getMyProfile(@AuthenticationPrincipal Jwt jwt) {
        User user = authenticatedUserResolver.resolve(jwt)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found"));
        Map<String, Object> response = new HashMap<>();
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
            personalInfo.put("departmentId",     p.getDepartmentId());
            personalInfo.put("department",       p.getDepartment()     != null ? p.getDepartment()      : "");
            personalInfo.put("departmentDescription", p.getDepartmentDescription() != null ? p.getDepartmentDescription() : "");
            personalInfo.put("departmentActive", p.getDepartmentActive());
            personalInfo.put("jobTitleId",       p.getJobTitleId());
            personalInfo.put("jobTitle",         p.getJobTitle()       != null ? p.getJobTitle()        : "");
            personalInfo.put("jobTitleDescription", p.getJobTitleDescription() != null ? p.getJobTitleDescription() : "");
            personalInfo.put("jobTitleActive",   p.getJobTitleActive());
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
            @RequestBody UpdateMyProfileRequest body,
            @AuthenticationPrincipal Jwt jwt) {

        Map<String, Object> response = new HashMap<>();
        User user = authenticatedUserResolver.resolve(jwt)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found"));
        if (user.getPerson() == null) {
            throw new ResourceNotFoundException("Profile not found");
        }

        if (body.hasField("department") || body.hasField("departmentId")) {
            throw new BadRequestException("Department is HR-managed employment data and cannot be changed from self-service.");
        }

        Person p = user.getPerson();

        if (body.hasField("phone")) {
            p.setPhone(body.getPhone());
        }
        if (body.hasField("address")) {
            p.setAddress(body.getAddress());
        }
        if (body.hasField("maritalStatus")) {
            p.setMaritalStatus(body.getMaritalStatus());
        }
        if (body.hasField("numberOfChildren") && body.getNumberOfChildren() != null) {
            p.setNumberOfChildren(body.getNumberOfChildren());
        }
        if (body.hasField("avatarPhoto")) {
            p.setAvatarPhoto(body.getAvatarPhoto());
        }
        if (body.hasField("avatarColor")) {
            p.setAvatarColor(body.getAvatarColor());
        }

        personRepository.save(p);

        response.put("success", true);
        response.put("message", "Profile updated successfully");
        return response;
    }

    /**
     * PATCH /api/hr/users/{userId}/employment
     * HR sets department, job title and hire date for any user.
     */
    @PreAuthorize("hasRole('HR_MANAGER')")
    @PatchMapping("/api/hr/users/{userId}/employment")
    public Map<String, Object> updateEmployment(
            @PathVariable Long userId,
            @RequestBody UpdateEmploymentRequest body,
            @AuthenticationPrincipal Jwt jwt) {

        Optional<User> userOpt = userRepository.findById(userId);
        Map<String, Object> response = new HashMap<>();
        if (userOpt.isEmpty() || userOpt.get().getPerson() == null) {
            response.put("success", false);
            response.put("message", "User not found");
            return response;
        }
        User targetUser = userOpt.get();
        if (isSameAuthenticatedUser(targetUser, jwt)) {
            throw new BadRequestException("You cannot update your own HR-managed employment details.");
        }

        Person p = targetUser.getPerson();
        if (body.getHireDate() != null) {
            p.setHireDate(body.getHireDate());
        }
        if (body.getDepartmentId() != null) {
            var department = departmentService.getDepartmentEntity(body.getDepartmentId());
            if (!Boolean.TRUE.equals(department.getActive()) && (p.getDepartmentRef() == null || !p.getDepartmentRef().getId().equals(department.getId()))) {
                throw new BadRequestException("Department '" + department.getName() + "' is archived and cannot be assigned.");
            }
            p.setDepartmentRef(department);
        } else {
            p.setDepartmentRef(null);
        }
        if (body.getJobTitleId() != null) {
            var jobTitle = jobTitleService.getJobTitleEntity(body.getJobTitleId());
            if (!Boolean.TRUE.equals(jobTitle.getActive()) && (p.getJobTitleRef() == null || !p.getJobTitleRef().getId().equals(jobTitle.getId()))) {
                throw new BadRequestException("Job title '" + jobTitle.getName() + "' is archived and cannot be assigned.");
            }
            p.setJobTitleRef(jobTitle);
        } else {
            p.setJobTitleRef(null);
        }
        // Salary stays system-controlled and is derived only from role.
        p.setSalary(employmentSalaryService.resolveEffectiveSalary(targetUser.getRole()));
        personRepository.save(p);
        response.put("success", true);
        response.put("message", "Employment details updated.");
        return response;
    }

    private boolean isSameAuthenticatedUser(User targetUser, Jwt jwt) {
        if (targetUser == null || jwt == null) {
            return false;
        }
        String actorKeycloakId = jwt.getSubject();
        if (actorKeycloakId != null && !actorKeycloakId.isBlank()
                && targetUser.getKeycloakId() != null
                && targetUser.getKeycloakId().equals(actorKeycloakId)) {
            return true;
        }
        String actorUsername = jwt.getClaimAsString("preferred_username");
        return actorUsername != null && !actorUsername.isBlank()
                && targetUser.getUsername() != null
                && targetUser.getUsername().equalsIgnoreCase(actorUsername);
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
