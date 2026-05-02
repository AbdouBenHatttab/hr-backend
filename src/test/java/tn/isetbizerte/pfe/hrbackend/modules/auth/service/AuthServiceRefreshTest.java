package tn.isetbizerte.pfe.hrbackend.modules.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import tn.isetbizerte.pfe.hrbackend.common.dto.LoginResponse;
import tn.isetbizerte.pfe.hrbackend.common.enums.TypeRole;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.service.UserService;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceRefreshTest {

    private RestTemplate restTemplate;
    private UserService userService;
    private JwtDecoder jwtDecoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        userService = mock(UserService.class);
        jwtDecoder = mock(JwtDecoder.class);
        authService = new AuthService(restTemplate, userService, jwtDecoder, new ObjectMapper());

        ReflectionTestUtils.setField(authService, "keycloakServerUrl", "http://localhost:8080");
        ReflectionTestUtils.setField(authService, "realm", "hr-realm");
        ReflectionTestUtils.setField(authService, "clientId", "hr-backend");
        ReflectionTestUtils.setField(authService, "clientSecret", "");
    }

    @Test
    void refreshUserToken_returnsUpdatedUserAndRoleInfo() {
        String url = "http://localhost:8080/realms/hr-realm/protocol/openid-connect/token";
        when(restTemplate.postForEntity(eq(url), any(), eq(Map.class))).thenReturn(ResponseEntity.ok(Map.of(
                "access_token", "new-access-token",
                "refresh_token", "new-refresh-token",
                "token_type", "Bearer",
                "expires_in", 300
        )));

        Jwt jwt = new Jwt(
                "new-access-token",
                Instant.now(),
                Instant.now().plusSeconds(300),
                Map.of("alg", "none"),
                Map.of(
                        "sub", "kc-user",
                        "preferred_username", "employee",
                        "email", "employee@example.com",
                        "realm_access", Map.of("roles", List.of("TEAM_LEADER"))
                )
        );
        when(jwtDecoder.decode("new-access-token")).thenReturn(jwt);

        User user = new User("kc-user", "employee");
        user.setRole(TypeRole.EMPLOYEE);
        user.setActive(true);
        when(userService.findByKeycloakId("kc-user")).thenReturn(Optional.of(user));

        LoginResponse response = authService.refreshUserToken("old-refresh-token");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getUsername()).isEqualTo("employee");
        assertThat(response.getEmail()).isEqualTo("employee@example.com");
        assertThat(response.getRoles()).containsExactly("TEAM_LEADER");
        assertThat(response.getAccessToken()).isEqualTo("new-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("new-refresh-token");
        verify(userService).updateUserRoleByUsername("employee", "TEAM_LEADER");
    }
}
