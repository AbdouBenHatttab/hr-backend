package tn.isetbizerte.pfe.hrbackend.infrastructure.outbox;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/hr/outbox")
public class OutboxAdminController {

    private final OutboxEventRepository repository;
    private final OutboxEventService outboxEventService;

    public OutboxAdminController(OutboxEventRepository repository, OutboxEventService outboxEventService) {
        this.repository = repository;
        this.outboxEventService = outboxEventService;
    }

    @PreAuthorize("hasRole('HR_MANAGER')")
    @GetMapping("/failed")
    public ResponseEntity<Map<String, Object>> getFailed() {
        List<OutboxEvent> failed = repository.findByStatus(OutboxStatus.FAILED);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("count", failed.size());
        response.put("data", failed);
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasRole('HR_MANAGER')")
    @PostMapping("/replay/{id}")
    public ResponseEntity<Map<String, Object>> replay(@PathVariable Long id) {
        outboxEventService.replay(id);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Outbox event replay queued");
        response.put("eventId", id);
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasRole('HR_MANAGER')")
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", outboxEventService.counts());
        return ResponseEntity.ok(response);
    }
}
