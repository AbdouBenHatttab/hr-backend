package tn.isetbizerte.pfe.hrbackend.modules.hr.service;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * Service for managing Keycloak roles via Admin API
 */
@Service
public class KeycloakAdminService {

    @Value("${keycloak.auth-server-url}")
    private String keycloakServerUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.admin.username}")
    private String adminUsername;

    @Value("${keycloak.admin.password}")
    private String adminPassword;

    /**
     * Get Keycloak Admin client instance
     */
    private Keycloak getKeycloakInstance() {
        return KeycloakBuilder.builder()
                .serverUrl(keycloakServerUrl)
                .realm("master")
                .username(adminUsername)
                .password(adminPassword)
                .clientId("admin-cli")
                .build();
    }

    /**
     * Assign a realm role to a user in Keycloak by keycloakId
     * @param keycloakUserId The Keycloak user ID (UUID)
     * @param roleName The role name to assign (e.g., "EMPLOYEE", "TEAM_LEADER")
     * @return true if successful, false otherwise
     */
    public boolean assignRoleToUser(String keycloakUserId, String roleName) {
        try (Keycloak keycloak = getKeycloakInstance()) {
            System.out.println("🔄 Starting role assignment...");
            System.out.println("   Keycloak Server: " + keycloakServerUrl);
            System.out.println("   Realm: " + realm);
            System.out.println("   User ID: " + keycloakUserId);
            System.out.println("   Role to assign: " + roleName);

            RealmResource realmResource = keycloak.realm(realm);
            UsersResource usersResource = realmResource.users();
            UserResource userResource = usersResource.get(keycloakUserId);

            System.out.println("✓ User resource obtained");

            // Get the role representation
            RoleRepresentation role = realmResource.roles().get(roleName).toRepresentation();

            if (role == null) {
                System.err.println("❌ ERROR: Role '" + roleName + "' does not exist in Keycloak realm '" + realm + "'");
                System.err.println("   Available roles must be created in Keycloak first!");
                return false;
            }

            System.out.println("✓ Role representation found: " + role.getName());

            // Get current roles
            List<RoleRepresentation> currentRoles = userResource.roles().realmLevel().listEffective();
            System.out.println("✓ Current roles: " + currentRoles.size() + " role(s)");

            for (RoleRepresentation cr : currentRoles) {
                System.out.println("   - " + cr.getName());
            }

            // Remove old application roles (keep only default roles)
            for (RoleRepresentation currentRole : currentRoles) {
                String currentRoleName = currentRole.getName();
                if (currentRoleName.equals("EMPLOYEE") ||
                    currentRoleName.equals("TEAM_LEADER") ||
                    currentRoleName.equals("HR_MANAGER") ||
                    currentRoleName.equals("NEW_USER")) {
                    System.out.println("   Removing old role: " + currentRoleName);
                    userResource.roles().realmLevel().remove(Collections.singletonList(currentRole));
                }
            }

            System.out.println("✓ Old roles removed");

            // Assign the new role
            System.out.println("🔄 Assigning new role: " + roleName);
            userResource.roles().realmLevel().add(Collections.singletonList(role));

            System.out.println("✅ SUCCESS: Role '" + roleName + "' assigned to user in Keycloak!");
            return true;

        } catch (NullPointerException e) {
            System.err.println("❌ ERROR (NullPointerException): " + e.getMessage());
            System.err.println("   Possible causes:");
            System.err.println("   1. Role does not exist in Keycloak");
            System.err.println("   2. User does not exist in Keycloak");
            System.err.println("   3. Keycloak connection failed");
            e.printStackTrace();
            return false;
        } catch (Exception e) {
            System.err.println("❌ ERROR assigning role in Keycloak: " + e.getMessage());
            System.err.println("   Exception type: " + e.getClass().getSimpleName());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Find Keycloak user by username
     * @param username The username to search for
     * @return Keycloak user ID (UUID) or null if not found
     */
    public String findKeycloakUserIdByUsername(String username) {
        try (Keycloak keycloak = getKeycloakInstance()) {
            RealmResource realmResource = keycloak.realm(realm);
            UsersResource usersResource = realmResource.users();

            List<UserRepresentation> users = usersResource.search(username, true);

            if (!users.isEmpty()) {
                return users.get(0).getId();
            }
            return null;
        } catch (Exception e) {
            System.err.println("Error finding Keycloak user: " + e.getMessage());
            return null;
        }
    }
}

