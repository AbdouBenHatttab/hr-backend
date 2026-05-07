package tn.isetbizerte.pfe.hrbackend.modules.assistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import tn.isetbizerte.pfe.hrbackend.modules.assistant.dto.AiServiceRequest;
import tn.isetbizerte.pfe.hrbackend.modules.assistant.dto.AiServiceResponse;
import tn.isetbizerte.pfe.hrbackend.modules.assistant.dto.AssistantChatRequest;
import tn.isetbizerte.pfe.hrbackend.modules.assistant.dto.AssistantChatResponse;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.service.AuthenticatedUserResolver;

import tn.isetbizerte.pfe.hrbackend.modules.assistant.dto.SafeAssistantContext;

import java.util.List;
import java.util.stream.Collectors;

/**
 * AssistantGatewayService
 * -----------------------
 * The single integration point between Spring Boot and the FastAPI AI service.
 *
 * Responsibilities:
 *  1. Resolve the authenticated user's role.
 *  2. Build the safe context via AssistantContextBuilder.
 *  3. POST to FastAPI /assistant/chat using the dedicated aiServiceRestTemplate.
 *  4. Map the FastAPI response to the public AssistantChatResponse — including
 *     Phase 3.1 drafting fields (draft, draftType, draftFields, missingFields).
 *  5. Return a safe fallback if FastAPI is unreachable, times out, or errors.
 *
 * NEVER forwards the JWT token to FastAPI.
 * NEVER leaks FastAPI error messages to the React caller.
 */
@Service
public class AssistantGatewayService {

    private static final Logger logger = LoggerFactory.getLogger(AssistantGatewayService.class);

    private static final String DISCLAIMER =
            "The assistant provides guidance only. Final decisions remain with authorized users.";

    private final RestTemplate aiServiceRestTemplate;
    private final AssistantContextBuilder contextBuilder;
    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final String aiServiceBaseUrl;

    public AssistantGatewayService(
            @Qualifier("aiServiceRestTemplate") RestTemplate aiServiceRestTemplate,
            AssistantContextBuilder contextBuilder,
            AuthenticatedUserResolver authenticatedUserResolver,
            @Value("${app.ai-service.base-url}") String aiServiceBaseUrl
    ) {
        this.aiServiceRestTemplate = aiServiceRestTemplate;
        this.contextBuilder = contextBuilder;
        this.authenticatedUserResolver = authenticatedUserResolver;
        this.aiServiceBaseUrl = aiServiceBaseUrl;
    }

    /**
     * Handle a chat request from React. Returns a guidance response from the AI service,
     * or a safe fallback response if the AI service is unavailable.
     */
    public AssistantChatResponse chat(AssistantChatRequest request, Jwt jwt) {
        try {
            // 1. Resolve role (never send the JWT itself to FastAPI)
            User user = authenticatedUserResolver.require(jwt);
            String role = user.getRole().name();

            // 2. Build safe context (no tokens, no private data, no entities)
            SafeAssistantContext context = contextBuilder.build(jwt, request.selectedLeaveRequestId());

            // 3. Assemble the internal request sent to FastAPI
            AiServiceRequest aiRequest = new AiServiceRequest(
                    role,
                    request.question(),
                    request.pageContext(),
                    context
            );

            // 4. Call FastAPI
            String url = aiServiceBaseUrl + "/assistant/chat";
            AiServiceResponse aiResponse = aiServiceRestTemplate.postForObject(
                    url,
                    aiRequest,
                    AiServiceResponse.class
            );

            // 5. Map to public response (null-safe in case FastAPI returns a partial response)
            return toPublicResponse(aiResponse);

        } catch (RestClientException e) {
            // FastAPI is down, unreachable, timed out, or returned a 5xx
            logger.warn("AI service call failed: {}", e.getMessage());
            return AssistantChatResponse.unavailable();
        } catch (Exception e) {
            // Any other unexpected failure — never propagate internal details
            logger.error("Unexpected error in assistant gateway", e);
            return AssistantChatResponse.unavailable();
        }
    }

    // ---------------------------------------------------------------------------
    // Mapping
    // ---------------------------------------------------------------------------

    /**
     * Maps the internal AiServiceResponse to the public AssistantChatResponse.
     * Handles null responses from RestTemplate (e.g., FastAPI returned 204 or empty body).
     *
     * Phase 3.1: draft, draftType, draftFields, missingFields are passed through
     * without modification. They are null / empty list for non-drafting responses.
     */
    private AssistantChatResponse toPublicResponse(AiServiceResponse aiResponse) {
        if (aiResponse == null) {
            logger.warn("AI service returned null response body");
            return AssistantChatResponse.unavailable();
        }

        List<AssistantChatResponse.RelatedPage> pages = List.of();
        if (aiResponse.relatedPages() != null) {
            pages = aiResponse.relatedPages().stream()
                    .filter(p -> p != null && p.label() != null && p.route() != null)
                    .map(p -> new AssistantChatResponse.RelatedPage(p.label(), p.route()))
                    .collect(Collectors.toList());
        }

        return new AssistantChatResponse(
                aiResponse.answer()    != null ? aiResponse.answer()    : "No response from assistant.",
                aiResponse.reasons()   != null ? aiResponse.reasons()   : List.of(),
                aiResponse.warnings()  != null ? aiResponse.warnings()  : List.of(),
                pages,
                aiResponse.disclaimer() != null ? aiResponse.disclaimer() : DISCLAIMER,
                aiResponse.aiGenerated(),
                // Phase 3.1 drafting fields — null for non-drafting responses
                aiResponse.draft(),
                aiResponse.draftType(),
                aiResponse.draftFields(),
                aiResponse.missingFields() != null ? aiResponse.missingFields() : List.of()
        );
    }
}
