package tn.isetbizerte.pfe.hrbackend.modules.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import tn.isetbizerte.pfe.hrbackend.modules.user.service.UserService;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceLogoutTest {

    private RestTemplate restTemplate;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        authService = new AuthService(
                restTemplate,
                mock(UserService.class),
                mock(JwtDecoder.class),
                new ObjectMapper()
        );

        ReflectionTestUtils.setField(authService, "keycloakServerUrl", "http://localhost:8080");
        ReflectionTestUtils.setField(authService, "realm", "hr-realm");
        ReflectionTestUtils.setField(authService, "clientId", "hr-backend");
        ReflectionTestUtils.setField(authService, "clientSecret", "");
    }

    @Test
    void logoutUser_callsKeycloakLogoutWhenRefreshTokenIsPresent() {
        String url = "http://localhost:8080/realms/hr-realm/protocol/openid-connect/logout";
        when(restTemplate.postForEntity(eq(url), any(), eq(Void.class))).thenReturn(ResponseEntity.noContent().build());

        Map<String, Object> result = authService.logoutUser("refresh-token");

        assertThat(result)
                .containsEntry("success", true)
                .containsEntry("remoteLogout", true)
                .containsEntry("message", "Logout completed");
        verify(restTemplate).postForEntity(eq(url), any(), eq(Void.class));
    }

    @Test
    void logoutUser_handlesMissingRefreshTokenGracefully() {
        Map<String, Object> result = authService.logoutUser(" ");

        assertThat(result)
                .containsEntry("success", true)
                .containsEntry("remoteLogout", false)
                .containsEntry("message", "Logout completed locally");
        verify(restTemplate, never()).postForEntity(any(String.class), any(), eq(Void.class));
    }

    @Test
    void logoutUser_handlesKeycloakLogoutFailureGracefully() {
        String url = "http://localhost:8080/realms/hr-realm/protocol/openid-connect/logout";
        when(restTemplate.postForEntity(eq(url), any(), eq(Void.class)))
                .thenThrow(new RestClientException("unavailable"));

        Map<String, Object> result = authService.logoutUser("refresh-token");

        assertThat(result)
                .containsEntry("success", true)
                .containsEntry("remoteLogout", false)
                .containsEntry("message", "Logout completed locally");
    }
}
