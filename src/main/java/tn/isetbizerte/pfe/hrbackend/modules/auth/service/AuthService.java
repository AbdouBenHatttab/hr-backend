package tn.isetbizerte.pfe.hrbackend.modules.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;
import tn.isetbizerte.pfe.hrbackend.common.constants.AppConstants;
import tn.isetbizerte.pfe.hrbackend.common.dto.LoginRequest;
import tn.isetbizerte.pfe.hrbackend.common.dto.LoginResponse;
import tn.isetbizerte.pfe.hrbackend.common.dto.RegisterRequest;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.Person;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.service.UserService;

import java.util.*;

/**
 * Service for authentication operations with Keycloak integration
 */
@Service
public class AuthService {

    @Value("${keycloak.auth-server-url:http://localhost:8080}")
    private String keycloakServerUrl;

    @Value("${keycloak.realm:hr-realm}")
    private String realm;

    @Value("${keycloak.admin.username:admin}")
    private String adminUsername;

    @Value("${keycloak.admin.password:admin}")
    private String adminPassword;

    @Value("${keycloak.client-id:admin-cli}")
    private String clientId;

    @Value("${keycloak.client-secret:}")
    private String clientSecret;

    private final RestTemplate restTemplate;
    private final UserService userService;

    public AuthService(RestTemplate restTemplate, UserService userService) {
        this.restTemplate = restTemplate;
        this.userService = userService;
    }

    /**
     * Get admin access token from Keycloak
     */
    private String getAdminToken() {
        String tokenUrl = keycloakServerUrl + "/realms/master/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", "admin-cli");
        body.add("username", adminUsername);
        body.add("password", adminPassword);
        body.add("grant_type", "password");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);

        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            return (String) response.getBody().get("access_token");
        }
        throw new RuntimeException("Failed to get admin token");
    }

    /**
     * Register a new user in Keycloak and local database
     */
    public Map<String, Object> registerUser(RegisterRequest registerRequest) {
        String adminToken = getAdminToken();
        String usersUrl = keycloakServerUrl + "/admin/realms/" + realm + "/users";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken);

        // Create user payload for Keycloak
        Map<String, Object> userPayload = new HashMap<>();
        userPayload.put("username", registerRequest.getUsername());
        userPayload.put("email", registerRequest.getEmail());
        userPayload.put("firstName", registerRequest.getFirstName());
        userPayload.put("lastName", registerRequest.getLastName());
        userPayload.put("enabled", true);
        userPayload.put("emailVerified", true);

        // Set password
        Map<String, Object> credential = new HashMap<>();
        credential.put("type", "password");
        credential.put("value", registerRequest.getPassword());
        credential.put("temporary", false);
        userPayload.put("credentials", Collections.singletonList(credential));

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(userPayload, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(usersUrl, request, String.class);

            if (response.getStatusCode() == HttpStatus.CREATED) {
                String keycloakUserId = getUserIdFromKeycloak(adminToken, registerRequest.getUsername());
                if (keycloakUserId != null) {
                    saveUserToDatabase(registerRequest, keycloakUserId);
                }

                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "User registered successfully");
                result.put("username", registerRequest.getUsername());
                return result;
            }
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", formatRegistrationError(e.getMessage()));
            return error;
        }

        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("message", "Registration failed. Please try again.");
        return error;
    }

    /**
     * Format registration error messages to be user-friendly
     */
    private String formatRegistrationError(String errorMessage) {
        if (errorMessage == null || errorMessage.isEmpty()) {
            return "Registration failed. Please try again.";
        }

        // Check for common error patterns and provide friendly messages
        if (errorMessage.contains("User exists with same email")) {
            return "This email address is already registered. Please use a different email or try logging in.";
        }

        if (errorMessage.contains("User exists with same username")) {
            return "This username is already taken. Please choose a different username.";
        }

        if (errorMessage.contains("409")) {
            return "This email or username is already in use. Please choose different credentials.";
        }

        if (errorMessage.contains("400")) {
            return "Invalid registration data. Please check your input and try again.";
        }

        if (errorMessage.contains("401") || errorMessage.contains("403")) {
            return "Registration service error. Please try again later.";
        }

        if (errorMessage.contains("500")) {
            return "A server error occurred during registration. Please try again later.";
        }

        // If we can't identify the error type, return a generic message but keep it helpful
        if (errorMessage.length() > 150) {
            return "Registration failed. Please check your email and username are unique and try again.";
        }

        return "Registration failed: " + errorMessage;
    }

    /**
     * Get user ID from Keycloak by username
     */
    private String getUserIdFromKeycloak(String adminToken, String username) {
        String searchUrl = keycloakServerUrl + "/admin/realms/" + realm + "/users?username=" + username;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<List> response = restTemplate.exchange(searchUrl, HttpMethod.GET, request, List.class);

        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null && !response.getBody().isEmpty()) {
            Map<String, Object> user = (Map<String, Object>) response.getBody().get(0);
            return (String) user.get("id");
        }
        return null;
    }

    /**
     * Save user and person data to local database
     */
    private void saveUserToDatabase(RegisterRequest registerRequest, String keycloakUserId) {
        try {
            Person person = new Person();
            person.setFirstName(registerRequest.getFirstName());
            person.setLastName(registerRequest.getLastName());
            person.setEmail(registerRequest.getEmail());
            person.setPhone(registerRequest.getPhone());
            person.setBirthDate(registerRequest.getBirthDate());
            person.setAddress(registerRequest.getAddress());
            person.setMaritalStatus(registerRequest.getMaritalStatus());
            person.setNumberOfChildren(registerRequest.getNumberOfChildren());

            User user = new User(keycloakUserId, registerRequest.getUsername());
            user.setEmailVerified(true);
            user.setActive(true);

            user.setPerson(person);
            person.setUser(user);

            userService.savePerson(person);
            userService.saveUser(user);
        } catch (Exception e) {
            // Silently handle error
        }
    }

    /**
     * Authenticate user with Keycloak
     */
    public LoginResponse loginUser(LoginRequest loginRequest, String ipAddress, String userAgent) {
        String tokenUrl = keycloakServerUrl + "/realms/" + realm + "/protocol/openid-connect/token";
        String username = loginRequest.getUsername();

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("client_id", clientId);
            if (clientSecret != null && !clientSecret.isEmpty()) {
                body.add("client_secret", clientSecret);
            }
            body.add("grant_type", "password");
            body.add("username", loginRequest.getUsername());
            body.add("password", loginRequest.getPassword());
            body.add("scope", "openid profile email roles");

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                userService.saveLoginHistory(username, ipAddress, userAgent, true);
                return processTokenResponse(response.getBody(), username);
            }

        } catch (RestClientException e) {
            userService.saveLoginHistory(username, ipAddress, userAgent, false);
            if (e.getMessage().contains("401")) {
                return new LoginResponse("Invalid username or password");
            } else if (e.getMessage().contains("400")) {
                return new LoginResponse("Bad request - Check Keycloak client configuration");
            }
        } catch (Exception e) {
            userService.saveLoginHistory(username, ipAddress, userAgent, false);
        }

        return new LoginResponse("Login failed");
    }

    /**
     * Process token response from Keycloak
     */
    private LoginResponse processTokenResponse(Map<String, Object> tokenData, String username) {
        try {
            String accessToken = (String) tokenData.get("access_token");
            String refreshToken = (String) tokenData.get("refresh_token");
            String tokenType = (String) tokenData.get("token_type");
            Integer expiresIn = (Integer) tokenData.get("expires_in");

            Map<String, Object> userInfo = parseJwtToken(accessToken);
            String email = (String) userInfo.get("email");
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) userInfo.get("roles");

            // ✅ NEW: Sync role from Keycloak to database
            if (roles != null && !roles.isEmpty()) {
                String primaryRole = roles.get(0);
                syncUserRoleToDatabase(username, primaryRole);
            }

            return new LoginResponse(
                accessToken,
                refreshToken,
                tokenType,
                expiresIn != null ? expiresIn : AppConstants.DEFAULT_TOKEN_EXPIRY_SECONDS,
                username,
                email,
                roles
            );
        } catch (Exception e) {
            return new LoginResponse("Error processing login response");
        }
    }

    /**
     * Sync user role from Keycloak to database
     */
    private void syncUserRoleToDatabase(String username, String roleFromKeycloak) {
        try {
            userService.updateUserRoleByUsername(username, roleFromKeycloak);
        } catch (Exception e) {
            // Log but don't fail - role sync is not critical for login
            // The JWT token has the correct role anyway
        }
    }

    /**
     * Parse JWT token to extract user information
     */
    private Map<String, Object> parseJwtToken(String accessToken) {
        try {
            String[] parts = accessToken.split("\\.");
            if (parts.length >= 2) {
                String payload = parts[1];
                while (payload.length() % 4 != 0) {
                    payload += "=";
                }

                byte[] decodedBytes = Base64.getDecoder().decode(payload);
                String decodedPayload = new String(decodedBytes);

                Map<String, Object> userInfo = new HashMap<>();

                String email = extractJsonField(decodedPayload, "email");
                if (email == null || email.isEmpty()) {
                    email = extractJsonField(decodedPayload, "preferred_username");
                }
                userInfo.put("email", email);

                // Extract only the primary role (EMPLOYEE, TEAM_LEADER, HR_MANAGER, NEW_USER)
                String primaryRole = extractPrimaryRole(decodedPayload);
                List<String> roles = new ArrayList<>();
                if (primaryRole != null) {
                    roles.add(primaryRole);
                }
                userInfo.put("roles", roles);

                return userInfo;
            }
        } catch (Exception e) {
            // Silently handle
        }
        return new HashMap<>();
    }

    /**
     * Extract only the primary role from JWT (NEW_USER, EMPLOYEE, TEAM_LEADER, HR_MANAGER)
     */
    private String extractPrimaryRole(String json) {
        List<String> primaryRoles = new ArrayList<>();

        // Extract all roles from realm_access
        if (json.contains("\"realm_access\"")) {
            primaryRoles.addAll(extractRolesFromRealmAccess(json));
        }

        // Filter to only the primary roles we care about
        String[] allowedRoles = {"HR_MANAGER", "TEAM_LEADER", "EMPLOYEE", "NEW_USER"};
        for (String allowed : allowedRoles) {
            if (primaryRoles.contains(allowed)) {
                return allowed; // Return the first matching primary role in order of priority
            }
        }

        return "NEW_USER"; // Default role if none found
    }

    private String extractJsonField(String json, String fieldName) {
        try {
            String searchPattern = "\"" + fieldName + "\":\"";
            int fieldStart = json.indexOf(searchPattern);
            if (fieldStart != -1) {
                fieldStart += searchPattern.length();
                int fieldEnd = json.indexOf("\"", fieldStart);
                if (fieldEnd > fieldStart) {
                    return json.substring(fieldStart, fieldEnd);
                }
            }
        } catch (Exception e) {
            // Silently handle
        }
        return null;
    }

    private List<String> extractRolesFromRealmAccess(String json) {
        List<String> roles = new ArrayList<>();
        try {
            int realmStart = json.indexOf("\"realm_access\":");
            if (realmStart != -1) {
                int rolesStart = json.indexOf("\"roles\":[", realmStart);
                if (rolesStart != -1) {
                    rolesStart += 9;
                    int rolesEnd = json.indexOf("]", rolesStart);
                    if (rolesEnd > rolesStart) {
                        String rolesArray = json.substring(rolesStart, rolesEnd);
                        String[] roleParts = rolesArray.split(",");
                        for (String role : roleParts) {
                            role = role.trim().replace("\"", "");
                            if (!role.isEmpty()) {
                                roles.add(role);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {

        }
        return roles;
    }
}

