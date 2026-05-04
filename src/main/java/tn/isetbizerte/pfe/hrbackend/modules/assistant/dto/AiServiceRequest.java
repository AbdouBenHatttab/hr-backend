package tn.isetbizerte.pfe.hrbackend.modules.assistant.dto;

import java.util.Map;

/**
 * Internal request body sent from Spring Boot to the FastAPI AI service.
 *
 * Hard rules enforced here:
 * - role is the TypeRole name only (EMPLOYEE / TEAM_LEADER / HR_MANAGER)
 * - context contains only safe, pre-sanitised data from AssistantContextBuilder
 * - NO JWT token, keycloakId, refresh token, password, salary,
 *   deductions, birth date, phone, address, avatar, or raw entities.
 */
public record AiServiceRequest(
        String role,
        String question,
        String pageContext,
        Map<String, Object> context
) {}
