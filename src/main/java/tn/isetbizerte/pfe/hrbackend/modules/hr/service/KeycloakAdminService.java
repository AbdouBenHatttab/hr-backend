package tn.isetbizerte.pfe.hrbackend.modules.hr.service;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Service for managing Keycloak roles via Admin API
 */
@Service
public class KeycloakAdminService {

    private static final Logger logger = LoggerFactory.getLogger(KeycloakAdminService.class);

    private static final List<String> APPLICATION_ROLES = List.of("EMPLOYEE", "TEAM_LEADER", "HR_MANAGER", "NEW_USER");

    private final Keycloak keycloak;

    @Value("${keycloak.auth-server-url}")
    private String keycloakServerUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.admin.username}")
    private String adminUsername;


    public KeycloakAdminService(Keycloak keycloak) {
        this.keycloak = keycloak;
    }

    /**
     * Assign a realm role to a user in Keycloak by keycloakId
     * @param keycloakUserId The Keycloak user ID (UUID)
     * @param roleName The role name to assign (e.g., "EMPLOYEE", "TEAM_LEADER")
     * @return true if successful, false otherwise
     */
    public boolean assignRoleToUser(String keycloakUserId, String roleName) {
        try {
            logger.info("Assigning role '{}' to Keycloak user '{}' in realm '{}'", roleName, keycloakUserId, realm);

            RealmResource realmResource = keycloak.realm(realm);
            UsersResource usersResource = realmResource.users();
            UserResource userResource = usersResource.get(keycloakUserId);

            RoleRepresentation role = resolveRealmRoleIgnoreCase(realmResource, roleName);
            if (role == null) {
                logger.error("Role '{}' not found in Keycloak realm '{}' (case-insensitive)", roleName, realm);
                return false;
            }

            logger.debug("Role '{}' found in Keycloak", role.getName());

            List<RoleRepresentation> currentRoles = userResource.roles().realmLevel().listEffective();
            logger.debug("User currently has {} role(s)", currentRoles.size());

            for (RoleRepresentation currentRole : currentRoles) {
                String currentRoleName = currentRole.getName();
                if (isApplicationRole(currentRoleName)) {
                    logger.debug("Removing old role: {}", currentRoleName);
                    userResource.roles().realmLevel().remove(Collections.singletonList(currentRole));
                }
            }

            userResource.roles().realmLevel().add(Collections.singletonList(role));
            logger.info("Role '{}' successfully assigned to Keycloak user '{}'", roleName, keycloakUserId);
            return true;

        } catch (Exception e) {
            logger.error("Failed to assign role '{}' to Keycloak user '{}' on server '{}' with admin '{}'",
                    roleName, keycloakUserId, keycloakServerUrl, adminUsername, e);
            return false;
        }
    }

    public boolean setUserEnabled(String keycloakUserId, boolean enabled) {
        try {
            logger.info("Setting Keycloak user '{}' enabled={} in realm '{}'", keycloakUserId, enabled, realm);

            RealmResource realmResource = keycloak.realm(realm);
            UserResource userResource = realmResource.users().get(keycloakUserId);

            UserRepresentation userRepresentation = userResource.toRepresentation();
            userRepresentation.setEnabled(enabled);
            userResource.update(userRepresentation);

            logger.info("Keycloak user '{}' enabled={}", keycloakUserId, enabled);
            return true;
        } catch (Exception e) {
            logger.error("Failed to set Keycloak user '{}' enabled={} on server '{}' with admin '{}'",
                    keycloakUserId, enabled, keycloakServerUrl, adminUsername, e);
            return false;
        }
    }

    private boolean isApplicationRole(String roleName) {
        if (roleName == null) return false;
        for (String r : APPLICATION_ROLES) {
            if (r.equalsIgnoreCase(roleName)) return true;
        }
        return false;
    }

    private RoleRepresentation resolveRealmRoleIgnoreCase(RealmResource realmResource, String requestedRoleName) {
        if (requestedRoleName == null || requestedRoleName.isBlank()) return null;

        List<String> candidates = new ArrayList<>();
        candidates.add(requestedRoleName);
        candidates.add(requestedRoleName.toUpperCase(Locale.ROOT));
        candidates.add(requestedRoleName.toLowerCase(Locale.ROOT));

        for (String name : candidates) {
            try {
                RoleRepresentation rep = realmResource.roles().get(name).toRepresentation();
                if (rep != null && rep.getName() != null) return rep;
            } catch (Exception ignored) {
                // fall through
            }
        }

        try {
            for (RoleRepresentation rep : realmResource.roles().list()) {
                if (rep != null && rep.getName() != null && rep.getName().equalsIgnoreCase(requestedRoleName)) {
                    return rep;
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to list realm roles for case-insensitive match: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Find Keycloak user by username
     * @param username The username to search for
     * @return Keycloak user ID (UUID) or null if not found
     */
    public String findKeycloakUserIdByUsername(String username) {
        try {
            RealmResource realmResource = keycloak.realm(realm);
            UsersResource usersResource = realmResource.users();

            List<UserRepresentation> users = usersResource.search(username, true);

            if (!users.isEmpty()) {
                logger.debug("Found Keycloak user '{}' with id '{}'", username, users.get(0).getId());
                return users.get(0).getId();
            }
            logger.warn("No Keycloak user found for username '{}' in realm '{}'", username, realm);
            return null;
        } catch (Exception e) {
            logger.error("Failed to find Keycloak user by username '{}'", username, e);
            return null;
        }
    }

    /**
     * Reset user password in Keycloak
     * @param keycloakUserId The Keycloak user ID (UUID)
     * @param newPassword The new password to set
     * @return true if successful, false otherwise
     */
    public boolean resetUserPassword(String keycloakUserId, String newPassword) {
        try {
            logger.info("Resetting password for Keycloak user '{}'", keycloakUserId);

            RealmResource realmResource = keycloak.realm(realm);
            UsersResource usersResource = realmResource.users();
            UserResource userResource = usersResource.get(keycloakUserId);

            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(newPassword);
            credential.setTemporary(false);

            userResource.resetPassword(credential);

            logger.info("Password successfully reset in Keycloak for user '{}'", keycloakUserId);
            return true;

        } catch (Exception e) {
            logger.error("Failed to reset password for Keycloak user '{}'", keycloakUserId, e);
            return false;
        }
    }
}
