package tn.isetbizerte.pfe.hrbackend.modules.assistant.dto;

import java.util.List;

/**
 * Response returned by Spring Boot to React after the AI service call.
 *
 * This mirrors the FastAPI response shape exactly. Spring Boot proxies
 * the content without modification. No internal data is added here.
 */
public record AssistantChatResponse(
        String answer,
        List<String> reasons,
        List<String> warnings,
        List<RelatedPage> relatedPages,
        String disclaimer,
        boolean aiGenerated
) {
    /**
     * A navigable platform page suggested alongside the assistant answer.
     */
    public record RelatedPage(String label, String route) {}

    /**
     * Static factory for a safe fallback response when the AI service is
     * unavailable. Never exposes internal error details to React.
     */
    public static AssistantChatResponse unavailable() {
        return new AssistantChatResponse(
                "The assistant service is temporarily unavailable. Please try again later.",
                List.of(),
                List.of("AI_SERVICE_UNAVAILABLE"),
                List.of(),
                "The assistant provides guidance only. Final decisions remain with authorized users.",
                false
        );
    }
}
