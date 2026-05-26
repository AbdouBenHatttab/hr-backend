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
import tn.isetbizerte.pfe.hrbackend.modules.assistant.dto.SafeAssistantContext;
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
import static org.mockito.Mockito.verify;
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

    /** Empty typed context used as a safe stub in gateway-level tests. */
    private static final SafeAssistantContext EMPTY_CONTEXT =
            new SafeAssistantContext(null, null, null, null, null);

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
        when(contextBuilder.build(jwt, null)).thenReturn(
                new SafeAssistantContext("Ahmed Ben Ali", null, null, null, null));

        AiServiceResponse aiResponse = new AiServiceResponse(
                "Your leave balance is 12 days.",
                List.of(),
                List.of(),
                List.of(new AiServiceResponse.RelatedPage("Leave Balance", "/leave/balance")),
                "The assistant provides guidance only.",
                true,
                null, null, null, List.of(), List.of()
        );

        when(aiServiceRestTemplate.postForObject(
                eq(BASE_URL + "/assistant/chat"),
                any(AiServiceRequest.class),
                eq(AiServiceResponse.class)
        )).thenReturn(aiResponse);

        AssistantChatResponse response = gatewayService.chat(
                new AssistantChatRequest("What is my leave balance?", "leave", null),
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
        when(contextBuilder.build(jwt, null)).thenReturn(EMPTY_CONTEXT);

        // Simulates a connection refused / timeout
        when(aiServiceRestTemplate.postForObject(anyString(), any(), eq(AiServiceResponse.class)))
                .thenThrow(new ResourceAccessException("Connection refused"));

        AssistantChatResponse response = gatewayService.chat(
                new AssistantChatRequest("Help", null, null),
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
        when(contextBuilder.build(jwt, null)).thenReturn(EMPTY_CONTEXT);

        when(aiServiceRestTemplate.postForObject(anyString(), any(), eq(AiServiceResponse.class)))
                .thenReturn(null);

        AssistantChatResponse response = gatewayService.chat(
                new AssistantChatRequest("Help", null, null),
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
        when(contextBuilder.build(jwt, null)).thenReturn(EMPTY_CONTEXT);

        AiServiceResponse aiResponse = new AiServiceResponse(
                "Guidance.", List.of(), List.of(), List.of(), "Disclaimer.", true,
                null, null, null, List.of(), List.of()
        );

        when(aiServiceRestTemplate.postForObject(anyString(), any(AiServiceRequest.class), eq(AiServiceResponse.class)))
                .thenAnswer(invocation -> {
                    AiServiceRequest req = invocation.getArgument(1);
                    // Confirm role is the string name, not a token
                    assertThat(req.role()).isEqualTo("EMPLOYEE");
                    // AiServiceRequest has no token field — compile-time guarantee
                    return aiResponse;
                });

        gatewayService.chat(new AssistantChatRequest("test", null, null), jwt);
    }

    @Test
    void chat_passesSelectedLeaveRequestIdToContextBuilder() {
        Jwt jwt = jwt("kc-lead");
        User user = user(TypeRole.TEAM_LEADER);
        when(authenticatedUserResolver.require(jwt)).thenReturn(user);
        when(contextBuilder.build(jwt, 99L)).thenReturn(EMPTY_CONTEXT);

        AiServiceResponse aiResponse = new AiServiceResponse(
                "Guidance.", List.of(), List.of(), List.of(), "Disclaimer.", true,
                null, null, null, List.of(), List.of()
        );

        when(aiServiceRestTemplate.postForObject(anyString(), any(AiServiceRequest.class), eq(AiServiceResponse.class)))
                .thenReturn(aiResponse);

        gatewayService.chat(new AssistantChatRequest("Help me review this leave", "team", 99L), jwt);

        verify(contextBuilder).build(jwt, 99L);
    }

    @Test
    void chat_outboundRequest_containsTeamLeaveDecisionAvailableWhenProvidedByContextBuilder() {
        Jwt jwt = jwt("kc-lead");
        User user = user(TypeRole.TEAM_LEADER);
        when(authenticatedUserResolver.require(jwt)).thenReturn(user);

        SafeAssistantContext ctxWithDecision = new SafeAssistantContext(
                "Leader Name",
                null,
                null,
                null,
                new SafeAssistantContext.TeamLeaveDecisionContext(
                        true,
                        null,
                        130L,
                        "Employee Name",
                        "ANNUAL",
                        java.time.LocalDate.of(2026, 6, 10),
                        java.time.LocalDate.of(2026, 6, 12),
                        2,
                        "PENDING",
                        "PENDING_TL",
                        "Reason",
                        0L,
                        0L,
                        5,
                        0,
                        5,
                        "NORMAL",
                        0L,
                        0L,
                        0L,
                        0L,
                        false,
                        true,
                        List.of(new SafeAssistantContext.TaskEvidenceSummary(
                                "Review docs",
                                "Portal",
                                "IN_PROGRESS",
                                "HIGH",
                                java.time.LocalDate.of(2026, 6, 13),
                                "HIGH_PRIORITY_DUE_DURING_LEAVE"
                        ))
                )
        );
        when(contextBuilder.build(jwt, 130L)).thenReturn(ctxWithDecision);

        AiServiceResponse aiResponse = new AiServiceResponse(
                "Guidance.", List.of(), List.of(), List.of(), "Disclaimer.", true,
                null, null, null, List.of(), List.of()
        );

        when(aiServiceRestTemplate.postForObject(anyString(), any(AiServiceRequest.class), eq(AiServiceResponse.class)))
                .thenAnswer(invocation -> {
                    AiServiceRequest req = invocation.getArgument(1);
                    assertThat(req.context()).isNotNull();
                    assertThat(req.context().teamLeaveDecision()).isNotNull();
                    assertThat(req.context().teamLeaveDecision().available()).isTrue();
                    assertThat(req.context().teamLeaveDecision().taskEvidence()).hasSize(1);
                    assertThat(req.context().teamLeaveDecision().taskEvidence().get(0).title()).isEqualTo("Review docs");
                    return aiResponse;
                });

        gatewayService.chat(new AssistantChatRequest("Help", "team", 130L), jwt);
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
