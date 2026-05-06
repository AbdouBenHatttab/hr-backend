package tn.isetbizerte.pfe.hrbackend.modules.assistant.dto;

/**
 * Internal request body sent from Spring Boot to the FastAPI AI service.
 *
 * Hard rules enforced here:
 * - role is the TypeRole name only (EMPLOYEE / TEAM_LEADER / HR_MANAGER)
 * - context is a typed {@link SafeAssistantContext} — never a raw Map,
 *   never a raw entity, never a JWT claim.
 * - NO JWT token, keycloakId, refresh token, password, salary,
 *   deductions, birth date, phone, address, avatar, or raw entities.
 */
public record AiServiceRequest(
        String role,
        String question,
        String pageContext,
        SafeAssistantContext context
) {}
