package tn.isetbizerte.pfe.hrbackend.modules.user.service;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import tn.isetbizerte.pfe.hrbackend.common.exception.ResourceNotFoundException;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.UserRepository;

import java.util.Optional;

@Service
public class AuthenticatedUserResolver {

    private final UserRepository userRepository;

    public AuthenticatedUserResolver(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<User> resolve(Jwt jwt) {
        if (jwt == null) {
            return Optional.empty();
        }

        String keycloakId = jwt.getSubject();
        if (keycloakId != null && !keycloakId.isBlank()) {
            Optional<User> byKeycloakId = userRepository.findByKeycloakId(keycloakId);
            if (byKeycloakId.isPresent()) {
                return byKeycloakId;
            }
        }

        String username = jwt.getClaimAsString("preferred_username");
        if (username != null && !username.isBlank()) {
            Optional<User> byUsernameIgnoreCase = userRepository.findByUsernameIgnoreCaseWithPerson(username);
            if (byUsernameIgnoreCase.isPresent()) {
                return byUsernameIgnoreCase;
            }
        }

        String email = jwt.getClaimAsString("email");
        if (email != null && !email.isBlank()) {
            Optional<User> byEmail = userRepository.findByPersonEmailIgnoreCaseWithPerson(email);
            if (byEmail.isPresent()) {
                return byEmail;
            }
        }

        return Optional.empty();
    }

    public User require(Jwt jwt) {
        return resolve(jwt).orElseThrow(() ->
                new ResourceNotFoundException("User not found for authenticated principal"));
    }
}
