package tn.isetbizerte.pfe.hrbackend.modules.assistant.dto;

import java.util.List;

/**
 * Internal representation of the FastAPI AI service response.
 *
 * Kept separate from AssistantChatResponse to allow independent
 * evolution of the internal AI wire format vs. the public API contract.
 * Currently the shapes are identical; the mapping layer in
 * AssistantGatewayService makes future divergence safe.
 */
public record AiServiceResponse(
        String answer,
        List<String> reasons,
        List<String> warnings,
        List<RelatedPage> relatedPages,
        String disclaimer,
        boolean aiGenerated
) {
    /**
     * Nested page record matching the FastAPI relatedPages shape:
     * [{ "label": "...", "route": "..." }]
     */
    public record RelatedPage(String label, String route) {}
}
