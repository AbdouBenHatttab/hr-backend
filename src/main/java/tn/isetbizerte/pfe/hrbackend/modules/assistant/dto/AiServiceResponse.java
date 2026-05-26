package tn.isetbizerte.pfe.hrbackend.modules.assistant.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Internal representation of the FastAPI AI service response.
 *
 * Kept separate from AssistantChatResponse to allow independent
 * evolution of the internal AI wire format vs. the public API contract.
 * The mapping layer in AssistantGatewayService makes future divergence safe.
 *
 * Phase 3.1 fields added:
 *   draft        — full draft text (null for non-drafting responses)
 *   draftType    — identifies the request type being drafted (null for non-drafting)
 *   draftFields  — extracted field values keyed by field name (null for non-drafting)
 *   missingFields — list of field names that could not be extracted (empty list for non-drafting)
 */
public record AiServiceResponse(
        String answer,
        List<String> reasons,
        List<String> warnings,
        List<RelatedPage> relatedPages,
        String disclaimer,
        boolean aiGenerated,
        // Phase 3.1: draft / structured fields — null for non-drafting responses
        String draft,
        String draftType,
        Map<String, Object> draftFields,
        List<String> missingFields,
        List<TaskEvidence> taskEvidence
) {
    /**
     * Nested page record matching the FastAPI relatedPages shape:
     * [{ "label": "...", "route": "..." }]
     */
    public record RelatedPage(String label, String route) {}

    public record TaskEvidence(
            String title,
            String projectName,
            String status,
            String priority,
            LocalDate dueDate,
            String impact
    ) {}
}
