package tn.isetbizerte.pfe.hrbackend.modules.assistant.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import tn.isetbizerte.pfe.hrbackend.common.enums.TypeRole;
import tn.isetbizerte.pfe.hrbackend.modules.assistant.dto.AiServiceRequest;
import tn.isetbizerte.pfe.hrbackend.modules.assistant.dto.AiServiceResponse;
import tn.isetbizerte.pfe.hrbackend.modules.assistant.dto.AssistantChatRequest;
import tn.isetbizerte.pfe.hrbackend.modules.assistant.dto.AssistantChatResponse;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.service.AuthenticatedUserResolver;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AssistantGatewayService.
 *
 * Verifies:
 *  - Successful FastAPI response is mapped and returned to React.
 *  - FastAPI unavailability returns the safe fallback (no exception thrown).
 *  - The JWT is never forwarded; only role string is used.
 *  - Null response from RestTemplate is handled safely.
 */
class AssistantGatewayServiceTest {

    private RestTemplate aiServiceRestTemplate;
    private AssistantContextBuilder contextBuilder;
    private AuthenticatedUserResolver authenticatedUserResolver;
    private AssistantGatewayService gatewayService;

    private static final String BASE_URL = "http://localhost:8000";

    @BeforeEach
    void setUp() {
        aiServiceRestTemplate      = mock(RestTemplate.class);
        contextBuilder             = mock(AssistantContextBuilder.class);
        authenticatedUserResolver  = mock(AuthenticatedUserResolver.class);

        gatewayService = new AssistantGatewayService(
                aiServiceRestTemplate,
                contextBuilder,
                authenticatedUserResolver,
                BASE_URL
        );
    }

    @Test
    void chat_returnsAssistantResponseOnSuccess() {
        Jwt jwt = jwt("kc-emp");
        User user = user(TypeRole.EMPLOYEE);
        when(authenticatedUserResolver.require(jwt)).thenReturn(user);
        when(contextBuilder.build(jwt)).thenReturn(Map.of("displayName", "Ahmed Ben Ali"));

        AiServiceResponse aiResponse = new AiServiceResponse(
                "Your leave balance is 12 days.",
                List.of(),
                List.of(),
                List.of(new AiServiceResponse.RelatedPage("Leave Balance", "/leave/balance")),
                "The assistant provides guidance only.",
                true
        );

        when(aiServiceRestTemplate.postForObject(
                eq(BASE_URL + "/assistant/chat"),
                any(AiServiceRequest.class),
                eq(AiServiceResponse.class)
        )).thenReturn(aiResponse);

        AssistantChatResponse response = gatewayService.chat(
                new AssistantChatRequest("What is my leave balance?", "leave"),
                jwt
        );

        assertThat(response.answer()).isEqualTo("Your leave balance is 12 days.");
        assertThat(response.aiGenerated()).isTrue();
        assertThat(response.relatedPages()).hasSize(1);
        assertThat(response.relatedPages().get(0).label()).isEqualTo("Leave Balance");
        assertThat(response.relatedPages().get(0).route()).isEqualTo("/leave/balance");
        assertThat(response.warnings()).isEmpty();
    }

    @Test
    void chat_returnsFallbackWhenFastApiIsUnreachable() {
        Jwt jwt = jwt("kc-emp");
        User user = user(TypeRole.EMPLOYEE);
        when(authenticatedUserResolver.require(jwt)).thenReturn(user);
        when(contextBuilder.build(jwt)).thenReturn(Map.of());

        // Simulates a connection refused / timeout
        when(aiServiceRestTemplate.postForObject(anyString(), any(), eq(AiServiceResponse.class)))
                .thenThrow(new ResourceAccessException("Connection refused"));

        AssistantChatResponse response = gatewayService.chat(
                new AssistantChatRequest("Help", null),
                jwt
        );

        assertThat(response.aiGenerated()).isFalse();
        assertThat(response.answer()).contains("temporarily unavailable");
        assertThat(response.warnings()).contains("AI_SERVICE_UNAVAILABLE");
        assertThat(response.relatedPages()).isEmpty();
    }

    @Test
    void chat_returnsFallbackWhenFastApiReturnsNull() {
        Jwt jwt = jwt("kc-emp");
        User user = user(TypeRole.EMPLOYEE);
        when(authenticatedUserResolver.require(jwt)).thenReturn(user);
        when(contextBuilder.build(jwt)).thenReturn(Map.of());

        when(aiServiceRestTemplate.postForObject(anyString(), any(), eq(AiServiceResponse.class)))
                .thenReturn(null);

        AssistantChatResponse response = gatewayService.chat(
                new AssistantChatRequest("Help", null),
                jwt
        );

        assertThat(response.aiGenerated()).isFalse();
        assertThat(response.warnings()).contains("AI_SERVICE_UNAVAILABLE");
    }

    @Test
    void chat_neverForwardsJwtToFastApi() {
        // Verified structurally: AssistantGatewayService constructs AiServiceRequest
        // with only role, question, pageContext, context — no token fields exist
        // on AiServiceRequest. This test confirms the role string is used, not the JWT.
        Jwt jwt = jwt("kc-emp");
        User user = user(TypeRole.EMPLOYEE);
        when(authenticatedUserResolver.require(jwt)).thenReturn(user);
        when(contextBuilder.build(jwt)).thenReturn(Map.of());

        AiServiceResponse aiResponse = new AiServiceResponse(
                "Guidance.", List.of(), List.of(), List.of(), "Disclaimer.", true
        );

        when(aiServiceRestTemplate.postForObject(anyString(), any(AiServiceRequest.class), eq(AiServiceResponse.class)))
                .thenAnswer(invocation -> {
                    AiServiceRequest req = invocation.getArgument(1);
                    // Confirm role is the string name, not a token
                    assertThat(req.role()).isEqualTo("EMPLOYEE");
                    // AiServiceRequest has no token field — compile-time guarantee
                    return aiResponse;
                });

        gatewayService.chat(new AssistantChatRequest("test", null), jwt);
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private Jwt jwt(String subject) {
        return new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "none"),
                Map.of("sub", subject, "preferred_username", subject)
        );
    }

    private User user(TypeRole role) {
        User u = new User("kc-id", "username");
        u.setRole(role);
        u.setActive(true);
        return u;
    }
}
