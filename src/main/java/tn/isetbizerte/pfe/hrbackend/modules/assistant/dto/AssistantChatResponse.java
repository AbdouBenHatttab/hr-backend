package tn.isetbizerte.pfe.hrbackend.modules.assistant.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Response returned by Spring Boot to React after the AI service call.
 *
 * This mirrors the FastAPI ChatResponse shape. Spring Boot proxies the content
 * without modification. No internal data (JWT, DB entities, etc.) is added here.
 *
 * Phase 3.1 fields:
 *   draft        — full draft text produced by the drafting assistant (null for non-drafting)
 *   draftType    — identifies the request type: "LEAVE_REQUEST" | "LOAN_REQUEST" |
 *                  "AUTHORIZATION_REQUEST" | "DOCUMENT_REQUEST" | "IMPROVE_TEXT"
 *                  null for non-drafting responses (backward-compatible).
 *   draftFields  — extracted field values keyed by field name; null values where
 *                  the user did not provide the information.
 *                  null for non-drafting and IMPROVE_TEXT responses.
 *   missingFields — list of field names that could not be extracted from the
 *                  user's input. Always a list (never null); empty for non-drafting.
 *                  Spring Boot uses this to drive the validate-draft call.
 */
public record AssistantChatResponse(
        String answer,
        List<String> reasons,
        List<String> warnings,
        List<RelatedPage> relatedPages,
        String disclaimer,
        boolean aiGenerated,
        // Phase 3.1: drafting fields — null/empty for non-drafting responses
        String draft,
        String draftType,
        Map<String, Object> draftFields,
        List<String> missingFields,
        List<TaskEvidence> taskEvidence
) {
    /**
     * A navigable platform page suggested alongside the assistant answer.
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
                false,
                null,
                null,
                null,
                List.of(),
                List.of()
        );
    }
}
