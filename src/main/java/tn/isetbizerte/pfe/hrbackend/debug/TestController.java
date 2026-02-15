package tn.isetbizerte.pfe.hrbackend.debug;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.isetbizerte.pfe.hrbackend.security.KeycloakRoleConverter;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * DEBUG CONTROLLER - For testing and development only
 * Should be disabled in production
 */
@RestController
@Profile("!prod")
public class TestController {

    @Autowired
    private KeycloakRoleConverter keycloakRoleConverter;

    @GetMapping("/public/hello")
    public Map<String, String> publicEndpoint() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "This is a public endpoint - no authentication required");
        return response;
    }

    @GetMapping("/api/new-user/waiting")
    public Map<String, Object> newUserEndpoint(@AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Your account is pending approval. Please wait for HR Manager to assign you a role.");
        response.put("status", "WAITING_FOR_APPROVAL");
        response.put("username", jwt.getClaimAsString("preferred_username"));
        response.put("email", jwt.getClaimAsString("email"));
        return response;
    }

    @GetMapping("/api/employee/info")
    public Map<String, Object> employeeEndpoint(@AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Hello Employee!");
        response.put("username", jwt.getClaimAsString("preferred_username"));
        response.put("email", jwt.getClaimAsString("email"));
        response.put("role", extractPrimaryRole(jwt));
        return response;
    }

    @GetMapping("/api/leader/info")
    public Map<String, Object> leaderEndpoint(@AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Hello Team Leader!");
        response.put("username", jwt.getClaimAsString("preferred_username"));
        response.put("email", jwt.getClaimAsString("email"));
        response.put("role", extractPrimaryRole(jwt));
        return response;
    }

    @GetMapping("/api/hr/info")
    public Map<String, Object> hrEndpoint(@AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Hello HR Manager!");
        response.put("username", jwt.getClaimAsString("preferred_username"));
        response.put("email", jwt.getClaimAsString("email"));
        response.put("role", extractPrimaryRole(jwt));
        return response;
    }

    @GetMapping("/api/test/token-info")
    public Map<String, Object> tokenInfo(@AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Token information");
        response.put("username", jwt.getClaimAsString("preferred_username"));
        response.put("email", jwt.getClaimAsString("email"));
        response.put("issuer", jwt.getClaimAsString("iss"));
        response.put("subject", jwt.getSubject());
        response.put("issuedAt", jwt.getIssuedAt());
        response.put("expiresAt", jwt.getExpiresAt());
        response.put("roles", jwt.getClaim("realm_access"));
        response.put("fullClaims", jwt.getClaims());
        return response;
    }

    @GetMapping("/api/hr/all-users")
    public Map<String, Object> getAllUsers(@AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "HR Manager can see this - List of all users");
        response.put("note", "This endpoint requires HR_MANAGER role");
        response.put("requestedBy", jwt.getClaimAsString("preferred_username"));
        response.put("userCount", "Would show actual user data from database");
        return response;
    }

    @GetMapping("/api/leader/team")
    public Map<String, Object> getTeam(@AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Team Leader Dashboard");
        response.put("note", "This endpoint requires TEAM_LEADER role");
        response.put("leader", jwt.getClaimAsString("preferred_username"));
        response.put("teamSize", "Would show actual team data");
        return response;
    }

    /**
     * Test role conversion endpoint
     */
    @GetMapping("/test-roles")
    public Map<String, Object> testRoleConversion(@AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> response = new HashMap<>();

        try {
            Collection<GrantedAuthority> authorities = keycloakRoleConverter.convert(jwt);

            response.put("success", true);
            response.put("username", jwt.getClaimAsString("preferred_username"));
            response.put("subject", jwt.getSubject());
            response.put("jwtClaims", jwt.getClaims().keySet());
            if (authorities != null) {
                response.put("authorities", authorities.stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(java.util.stream.Collectors.toList()));
            }
            response.put("realmAccess", jwt.getClaim("realm_access"));
            response.put("message", "Role conversion test completed");

        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }

        return response;
    }

    /**
     * Helper method to extract only the primary role from JWT
     */
    private String extractPrimaryRole(Jwt jwt) {
        try {
            Map<String, Object> realmAccess = (Map<String, Object>) jwt.getClaim("realm_access");
            if (realmAccess != null) {
                java.util.List<String> roles = (java.util.List<String>) realmAccess.get("roles");
                if (roles != null) {
                    String[] priorityRoles = {"HR_MANAGER", "TEAM_LEADER", "EMPLOYEE", "NEW_USER"};
                    for (String priority : priorityRoles) {
                        if (roles.contains(priority)) {
                            return priority;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Silently handle
        }
        return "NEW_USER";
    }
}

