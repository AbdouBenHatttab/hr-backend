package tn.isetbizerte.pfe.hrbackend.modules.user.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.UserRepository;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthenticatedUserResolverTest {

    private UserRepository userRepository;
    private AuthenticatedUserResolver resolver;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        resolver = new AuthenticatedUserResolver(userRepository);
    }

    @Test
    void resolvesByKeycloakIdEvenWhenPreferredUsernameCaseDiffers() {
        User user = new User("kc-123", "PariaturDelenitie");
        Jwt jwt = jwt("kc-123", "pariaturdelenitie", "abdoubenhattab802@gmail.com");

        when(userRepository.findByKeycloakId("kc-123")).thenReturn(Optional.of(user));

        Optional<User> resolved = resolver.resolve(jwt);

        assertTrue(resolved.isPresent());
        assertEquals("PariaturDelenitie", resolved.get().getUsername());
    }

    @Test
    void fallsBackToCaseInsensitiveUsernameLookup() {
        User user = new User("kc-456", "Tenetur");
        Jwt jwt = jwt("", "tenetur", "piteda@mailinator.com");

        when(userRepository.findByUsernameIgnoreCaseWithPerson("tenetur")).thenReturn(Optional.of(user));

        Optional<User> resolved = resolver.resolve(jwt);

        assertTrue(resolved.isPresent());
        assertEquals("Tenetur", resolved.get().getUsername());
    }

    private Jwt jwt(String subject, String preferredUsername, String email) {
        return new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "none"),
                Map.of(
                        "sub", subject,
                        "preferred_username", preferredUsername,
                        "email", email
                )
        );
    }
}
