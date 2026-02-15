package tn.isetbizerte.pfe.hrbackend.debug;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * DEBUG CONTROLLER - For JWT token debugging only
 * Should be disabled in production
 */
@RestController
@RequestMapping("/debug")
@Profile("!prod")
public class DebugController {

    /**
     * Debug endpoint to manually decode JWT token
     */
    @PostMapping("/decode-token")
    public Map<String, Object> decodeToken(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();

        try {
            String token = request.get("token");
            if (token == null || token.isEmpty()) {
                response.put("error", "Token is required");
                return response;
            }

            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }

            String[] parts = token.split("\\.");
            if (parts.length < 3) {
                response.put("error", "Invalid JWT format");
                return response;
            }

            String header = decodeBase64(parts[0]);
            response.put("header", header);

            String payload = decodeBase64(parts[1]);
            response.put("payload", payload);

            Map<String, Object> analysis = analyzePayload(payload);
            response.put("analysis", analysis);

            response.put("success", true);

        } catch (Exception e) {
            response.put("error", "Failed to decode token: " + e.getMessage());
        }

        return response;
    }

    private String decodeBase64(String encoded) {
        try {
            while (encoded.length() % 4 != 0) {
                encoded += "=";
            }
            byte[] decodedBytes = Base64.getDecoder().decode(encoded);
            return new String(decodedBytes);
        } catch (Exception e) {
            return "Failed to decode: " + e.getMessage();
        }
    }

    private Map<String, Object> analyzePayload(String payload) {
        Map<String, Object> analysis = new HashMap<>();

        analysis.put("contains_realm_access", payload.contains("realm_access"));
        analysis.put("contains_resource_access", payload.contains("resource_access"));
        analysis.put("contains_roles", payload.contains("\"roles\""));
        analysis.put("contains_email", payload.contains("\"email\""));
        analysis.put("contains_preferred_username", payload.contains("preferred_username"));

        if (payload.contains("\"preferred_username\":\"")) {
            String username = extractField(payload, "preferred_username");
            analysis.put("username", username);
        }

        if (payload.contains("\"email\":")) {
            String email = extractField(payload, "email");
            analysis.put("email", email);
        }

        List<String> possibleRoles = new ArrayList<>();
        if (payload.contains("realm_access")) {
            possibleRoles.add("Found realm_access section");
        }
        if (payload.contains("HR_MANAGER")) {
            possibleRoles.add("Contains HR_MANAGER string");
        }
        if (payload.contains("NEW_USER")) {
            possibleRoles.add("Contains NEW_USER string");
        }
        if (payload.contains("EMPLOYEE")) {
            possibleRoles.add("Contains EMPLOYEE string");
        }

        analysis.put("role_indicators", possibleRoles);

        return analysis;
    }

    private String extractField(String json, String fieldName) {
        try {
            String searchPattern = "\"" + fieldName + "\":\"";
            int start = json.indexOf(searchPattern);
            if (start != -1) {
                start += searchPattern.length();
                int end = json.indexOf("\"", start);
                if (end > start) {
                    return json.substring(start, end);
                }
            }
        } catch (Exception e) {
            return "Error extracting " + fieldName;
        }
        return "Not found";
    }

    /**
     * Test Keycloak connectivity
     */
    @GetMapping("/test-keycloak")
    public Map<String, Object> testKeycloakConnection() {
        Map<String, Object> response = new HashMap<>();

        try {
            String testUrl = "http://localhost:8080/realms/hr-realm";
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> keycloakResponse = restTemplate.getForEntity(testUrl, String.class);

            if (keycloakResponse.getStatusCode().is2xxSuccessful()) {
                response.put("success", true);
                response.put("message", "Keycloak connection successful");
                response.put("status", keycloakResponse.getStatusCode());
                response.put("realmUrl", testUrl);
            } else {
                response.put("success", false);
                response.put("message", "Keycloak connection failed");
                response.put("status", keycloakResponse.getStatusCode());
            }

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Keycloak connection error: " + e.getMessage());
            response.put("error", e.getClass().getSimpleName());
        }

        return response;
    }
}

