package tn.isetbizerte.pfe.hrbackend.modules.history.dto;

import java.time.LocalDateTime;

public record RequestHistoryDto(
        Long id,
        Long requestId,
        String type,
        String action,
        String fromState,
        String toState,
        String comment,
        LocalDateTime timestamp,
        String actorId,
        String actorUsername,
        String actorDisplayName,
        String actorRole
) {
}
