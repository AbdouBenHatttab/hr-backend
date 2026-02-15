package tn.isetbizerte.pfe.hrbackend.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.UserRepository;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class KeycloakRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static UserRepository staticUserRepository;

    @Autowired
    public void setUserRepository(UserRepository userRepository) {
        KeycloakRoleConverter.staticUserRepository = userRepository;
    }

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Set<String> roles = new HashSet<>();

        // Method 1: Try realm_access.roles (standard Keycloak)
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null && realmAccess.get("roles") != null) {
            @SuppressWarnings("unchecked")
            Collection<String> realmRoles = (Collection<String>) realmAccess.get("roles");
            roles.addAll(realmRoles);
        } else {
            // Get all possible identifiers
            String keycloakUserId = jwt.getSubject();
            String username = jwt.getClaimAsString("preferred_username");

            if (staticUserRepository != null) {
                try {
                    User foundUser = null;

                    // Try 1: Find by keycloakId
                    if (keycloakUserId != null && foundUser == null) {
                        Optional<User> userOpt = staticUserRepository.findByKeycloakId(keycloakUserId);
                        if (userOpt.isPresent()) {
                            foundUser = userOpt.get();
                        }
                    }

                    // Try 2: Find by username
                    if (username != null && foundUser == null) {
                        Optional<User> userOpt = staticUserRepository.findByUsername(username);
                        if (userOpt.isPresent()) {
                            foundUser = userOpt.get();
                        }
                    }

                    // If user found, get their role
                    if (foundUser != null && foundUser.getRole() != null) {
                        String roleName = foundUser.getRole().name();
                        roles.add(roleName);
                    }

                } catch (Exception e) {
                    // Silently handle error
                }
            }

            // Last resort: hardcoded fallback
            if (roles.isEmpty()) {
                // Check if username contains patterns
                if (username != null && username.toLowerCase().contains("hr")) {
                    roles.add("HR_MANAGER");
                } else {
                    roles.add("NEW_USER");
                }
            }
        }

        // Convert to Spring Security authorities
        Collection<GrantedAuthority> grantedAuthorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toList());


        return grantedAuthorities;
    }
}

