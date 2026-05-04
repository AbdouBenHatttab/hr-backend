package tn.isetbizerte.pfe.hrbackend.modules.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body sent by React to POST /api/assistant/chat.
 *
 * React provides only the question and optional page context.
 * Spring Boot resolves the authenticated user and builds the full
 * context internally before forwarding to the FastAPI AI service.
 *
 * No tokens, IDs, or sensitive data come from React.
 */
public record AssistantChatRequest(

        @NotBlank(message = "question is required")
        @Size(max = 1000, message = "question must not exceed 1000 characters")
        String question,

        String pageContext
) {}
