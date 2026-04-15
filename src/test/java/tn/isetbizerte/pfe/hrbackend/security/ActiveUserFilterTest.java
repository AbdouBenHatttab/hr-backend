package tn.isetbizerte.pfe.hrbackend.security;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import tn.isetbizerte.pfe.hrbackend.common.enums.TypeRole;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.UserRepository;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ActiveUserFilterTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final ActiveUserFilter filter = new ActiveUserFilter(userRepository);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_blocksDeactivatedApplicationUser() throws ServletException, IOException {
        User user = localUser(TypeRole.EMPLOYEE, false);
        when(userRepository.findByKeycloakId("kc-user")).thenReturn(Optional.of(user));
        authenticate("kc-user", "employee");

        MockHttpServletResponse response = doFilter("/api/me");

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("Account is deactivated");
    }

    @Test
    void doFilter_allowsReactivatedApplicationUser() throws ServletException, IOException {
        User user = localUser(TypeRole.EMPLOYEE, true);
        when(userRepository.findByKeycloakId("kc-user")).thenReturn(Optional.of(user));
        authenticate("kc-user", "employee");

        MockHttpServletResponse response = doFilter("/api/me");

        assertThat(response.getStatus()).isEqualTo(200);
    }

    private MockHttpServletResponse doFilter(String path) throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private void authenticate(String subject, String username) {
        Jwt jwt = new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(300),
                Map.of("alg", "none"),
                Map.of("sub", subject, "preferred_username", username)
        );
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private User localUser(TypeRole role, boolean active) {
        User user = new User("kc-user", "employee");
        user.setRole(role);
        user.setActive(active);
        return user;
    }
}
