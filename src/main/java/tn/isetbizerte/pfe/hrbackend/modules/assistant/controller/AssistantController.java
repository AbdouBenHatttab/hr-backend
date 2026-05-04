package tn.isetbizerte.pfe.hrbackend.modules.assistant.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.isetbizerte.pfe.hrbackend.modules.assistant.dto.AssistantChatRequest;
import tn.isetbizerte.pfe.hrbackend.modules.assistant.dto.AssistantChatResponse;
import tn.isetbizerte.pfe.hrbackend.modules.assistant.service.AssistantGatewayService;

/**
 * AssistantController
 * -------------------
 * Secure gateway endpoint for the ArabSoft AI assistant.
 *
 * Architecture: React -> Spring Boot (/api/assistant/chat) -> FastAPI (/assistant/chat)
 *
 * React must NEVER call FastAPI directly.
 * This controller enforces authentication, delegates all context building
 * to AssistantGatewayService, and returns only guidance responses.
 *
 * This endpoint is read-only: it does not create, update, approve,
 * reject, or delete any data.
 */
@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private final AssistantGatewayService assistantGatewayService;

    public AssistantController(AssistantGatewayService assistantGatewayService) {
        this.assistantGatewayService = assistantGatewayService;
    }

    /**
     * POST /api/assistant/chat
     *
     * Accepts a question from an authenticated user and returns AI-generated guidance.
     * NEW_USER role is explicitly excluded — they have no platform context to offer.
     *
     * @param request   The user's question and optional page context from React.
     * @param jwt       The validated JWT injected by Spring Security.
     * @return          The assistant guidance response.
     */
    @PostMapping("/chat")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'TEAM_LEADER', 'HR_MANAGER')")
    public ResponseEntity<AssistantChatResponse> chat(
            @Valid @RequestBody AssistantChatRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        AssistantChatResponse response = assistantGatewayService.chat(request, jwt);
        return ResponseEntity.ok(response);
    }
}
