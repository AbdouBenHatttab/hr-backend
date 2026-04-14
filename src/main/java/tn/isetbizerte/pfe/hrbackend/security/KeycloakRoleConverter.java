package tn.isetbizerte.pfe.hrbackend.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.UserRepository;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class KeycloakRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final Logger logger = LoggerFactory.getLogger(KeycloakRoleConverter.class);

    private final UserRepository userRepository;

    public KeycloakRoleConverter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Set<String> roles = new HashSet<>();

        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null && realmAccess.get("roles") != null) {
            @SuppressWarnings("unchecked")
            Collection<String> realmRoles = (Collection<String>) realmAccess.get("roles");
            for (String r : realmRoles) {
                if (r != null && !r.isBlank()) {
                    roles.add(r.toUpperCase(Locale.ROOT));
                }
            }
        } else {
            String keycloakUserId = jwt.getSubject();
            String username = jwt.getClaimAsString("preferred_username");

            try {
                    Optional<User> byKeycloakId = keycloakUserId == null
                            ? Optional.empty()
                            : userRepository.findByKeycloakId(keycloakUserId);
                    Optional<User> byUsername = username == null
                            ? Optional.empty()
                            : userRepository.findByUsername(username);

                    User foundUser = byKeycloakId.orElseGet(() -> byUsername.orElse(null));
                    if (foundUser != null && foundUser.getRole() != null) {
                        roles.add(foundUser.getRole().name());
                    }
                } catch (Exception e) {
                    logger.warn("Failed DB fallback role lookup for jwt subject '{}'", keycloakUserId, e);
                }

            if (roles.isEmpty()) {
                logger.warn("No roles found in JWT or DB for username '{}', defaulting to NEW_USER", username);
                roles.add("NEW_USER");
            }
        }

        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toList());
    }
}
