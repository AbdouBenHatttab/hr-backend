package tn.isetbizerte.pfe.hrbackend.modules.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import tn.isetbizerte.pfe.hrbackend.common.constants.AppConstants;
import tn.isetbizerte.pfe.hrbackend.common.dto.LoginRequest;
import tn.isetbizerte.pfe.hrbackend.common.dto.LoginResponse;
import tn.isetbizerte.pfe.hrbackend.common.dto.RegisterRequest;
import tn.isetbizerte.pfe.hrbackend.modules.user.service.UserService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for authentication operations with Keycloak integration
 */
@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    @Value("${keycloak.auth-server-url:http://localhost:8080}")
    private String keycloakServerUrl;

    @Value("${keycloak.realm:hr-realm}")
    private String realm;

    @Value("${keycloak.admin.username}")
    private String adminUsername;

    @Value("${keycloak.admin.password}")
    private String adminPassword;

    @Value("${keycloak.client-id:admin-cli}")
    private String clientId;

    @Value("${keycloak.client-secret:}")
    private String clientSecret;

    private final RestTemplate restTemplate;
    private final UserService userService;
    private final JwtDecoder jwtDecoder;
    private final ObjectMapper objectMapper;

    public AuthService(RestTemplate restTemplate, UserService userService, JwtDecoder jwtDecoder, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.userService = userService;
        this.jwtDecoder = jwtDecoder;
        this.objectMapper = objectMapper;
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
                } else {
                    throw new IllegalStateException("Registered user was not found in Keycloak after creation");
                }

                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "User registered successfully");
                result.put("username", registerRequest.getUsername());
                return result;
            }
        } catch (Exception e) {
            logger.warn("Registration failed for username '{}': {}", registerRequest.getUsername(), e.getMessage());
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
        userService.saveRegisteredUser(registerRequest, keycloakUserId);
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
        } catch (RestClientResponseException e) {
            userService.saveLoginHistory(username, ipAddress, userAgent, false);
            logger.warn("Login rejected for '{}' with status {} and body {}", username, e.getStatusCode().value(), e.getResponseBodyAsString());
            return new LoginResponse(mapKeycloakLoginError(e));
        } catch (RestClientException e) {
            userService.saveLoginHistory(username, ipAddress, userAgent, false);
            logger.warn("Login failed for '{}': {}", username, e.getMessage());
            return new LoginResponse("Authentication service unreachable. Please try again.");
        } catch (Exception e) {
            userService.saveLoginHistory(username, ipAddress, userAgent, false);
            logger.error("Unexpected error while logging in user '{}'", username, e);
        }

        return new LoginResponse("Login failed");
    }

    private String mapKeycloakLoginError(RestClientResponseException e) {
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
        String body = e.getResponseBodyAsString();
        String error = extractJsonField(body, "error");

        if ("invalid_grant".equals(error)) {
            return "Invalid username or password";
        }
        if ("unauthorized_client".equals(error)) {
            return "Keycloak client is not allowed to use password login (Direct Access Grants)";
        }
        if ("invalid_client".equals(error)) {
            return "Invalid Keycloak client configuration";
        }
        if ("invalid_request".equals(error)) {
            return "Invalid login request";
        }

        if (status != null && status.is5xxServerError()) {
            return "Authentication server error. Please try again later.";
        }
        if (status != null && status.is4xxClientError()) {
            return "Login request rejected by authentication server";
        }
        return "Login failed";
    }

    private String extractJsonField(String json, String key) {
        if (json == null || json.isBlank()) {
            return "";
        }
        try {
            JsonNode rootNode = objectMapper.readTree(json);
            return rootNode.path(key).asText("");
        } catch (Exception e) {
            logger.warn("Failed to parse Keycloak error response body for key '{}'", key, e);
            return "";
        }
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
            logger.error("Failed to process token response for user '{}'", username, e);
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
            logger.warn("Could not sync role '{}' for user '{}'", roleFromKeycloak, username, e);
        }
    }

    /**
     * Parse JWT token to extract user information
     */
    private Map<String, Object> parseJwtToken(String accessToken) {
        Map<String, Object> userInfo = new HashMap<>();
        try {
            Jwt jwt = jwtDecoder.decode(accessToken);

            String email = jwt.getClaimAsString("email");
            if (email == null || email.isEmpty()) {
                email = jwt.getClaimAsString("preferred_username");
            }
            userInfo.put("email", email);

            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            List<String> realmRoles = new ArrayList<>();
            if (realmAccess != null && realmAccess.get("roles") instanceof List<?> rolesClaim) {
                for (Object role : rolesClaim) {
                    if (role != null) {
                        realmRoles.add(role.toString());
                    }
                }
            }

            String primaryRole = extractPrimaryRoleFromRoles(realmRoles);
            userInfo.put("roles", Collections.singletonList(primaryRole));
            return userInfo;
        } catch (Exception e) {
            logger.error("Failed to decode JWT access token", e);
            userInfo.put("roles", Collections.singletonList("NEW_USER"));
            return userInfo;
        }
    }

    /**
     * Extract only the primary role from a list of roles
     */
    private String extractPrimaryRoleFromRoles(List<String> roles) {
        String[] allowedRoles = {"HR_MANAGER", "TEAM_LEADER", "EMPLOYEE", "NEW_USER"};
        for (String allowed : allowedRoles) {
            if (roles.contains(allowed)) {
                return allowed;
            }
        }
        return "NEW_USER";
    }
}
