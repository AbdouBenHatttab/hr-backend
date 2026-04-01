package tn.isetbizerte.pfe.hrbackend.modules.requests.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import tn.isetbizerte.pfe.hrbackend.modules.requests.dto.CreateLoanRequestDto;
import tn.isetbizerte.pfe.hrbackend.modules.requests.service.RequestsService;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@RestController
public class RequestsController {

    private final RequestsService service;

    public RequestsController(RequestsService service) {
        this.service = service;
    }

    // ══════════════════════════════════════════════════════════════
    // DOCUMENT REQUESTS
    // ══════════════════════════════════════════════════════════════

    @PreAuthorize("hasAnyRole('EMPLOYEE','TEAM_LEADER','HR_MANAGER')")
    @PostMapping("/api/employee/documents")
    public ResponseEntity<Map<String, Object>> createDocumentRequest(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        var data = service.createDocumentRequest(username,
                (String) body.get("documentType"),
                (String) body.get("notes"));
        return ok("Document request submitted.", data);
    }

    @PreAuthorize("hasAnyRole('EMPLOYEE','TEAM_LEADER','HR_MANAGER')")
    @GetMapping("/api/employee/documents")
    public ResponseEntity<Map<String, Object>> getMyDocuments(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Map<String, Object>> list = service.getMyDocumentRequests(jwt.getClaimAsString("preferred_username"), pageable);
        return okPage("My document requests.", list, page, size);
    }

    @PreAuthorize("hasRole('HR_MANAGER')")
    @GetMapping("/api/hr/documents")
    public ResponseEntity<Map<String, Object>> getAllDocuments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return okPage("All document requests.", service.getAllDocumentRequests(pageable), page, size);
    }

    @PreAuthorize("hasRole('HR_MANAGER')")
    @PostMapping("/api/hr/documents/{id}/approve")
    public ResponseEntity<Map<String, Object>> approveDocument(@PathVariable Long id,
                                                                @RequestBody(required = false) Map<String, Object> body,
                                                                @AuthenticationPrincipal Jwt jwt) {
        String note = body != null ? (String) body.getOrDefault("hrNote", "") : "";
        return ok("Document request approved.", service.decideDocument(id, true, note, jwt.getSubject()));
    }

    @PreAuthorize("hasRole('HR_MANAGER')")
    @PostMapping("/api/hr/documents/{id}/reject")
    public ResponseEntity<Map<String, Object>> rejectDocument(@PathVariable Long id,
                                                               @RequestBody(required = false) Map<String, Object> body,
                                                               @AuthenticationPrincipal Jwt jwt) {
        String note = body != null ? (String) body.getOrDefault("hrNote", "") : "";
        return ok("Document request rejected.", service.decideDocument(id, false, note, jwt.getSubject()));
    }

    // ══════════════════════════════════════════════════════════════
    // LOAN REQUESTS
    // ══════════════════════════════════════════════════════════════

    @PreAuthorize("hasAnyRole('EMPLOYEE','TEAM_LEADER','HR_MANAGER')")
    @PostMapping("/api/employee/loans")
    public ResponseEntity<Map<String, Object>> createLoanRequest(
            @Valid @RequestBody CreateLoanRequestDto body,
            @AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        var data = service.createLoanRequest(username,
                body.getType(), body.getAmount(), body.getRepaymentMonths(), body.getReason());
        return ok("Loan request submitted.", data);
    }

    @PreAuthorize("hasAnyRole('EMPLOYEE','TEAM_LEADER','HR_MANAGER')")
    @GetMapping("/api/employee/loans")
    public ResponseEntity<Map<String, Object>> getMyLoans(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Map<String, Object>> list = service.getMyLoanRequests(jwt.getClaimAsString("preferred_username"), pageable);
        return okPage("My loan requests.", list, page, size);
    }

    @PreAuthorize("hasAnyRole('EMPLOYEE','TEAM_LEADER','HR_MANAGER')")
    @GetMapping("/api/employee/loans/eligibility")
    public ResponseEntity<Map<String, Object>> getLoanEligibility(@AuthenticationPrincipal Jwt jwt) {
        var data = service.getLoanEligibility(jwt.getClaimAsString("preferred_username"));
        return ok("Loan eligibility info.", data);
    }

    @PreAuthorize("hasRole('HR_MANAGER')")
    @GetMapping("/api/hr/loans")
    public ResponseEntity<Map<String, Object>> getAllLoans(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return okPage("All loan requests.", service.getAllLoanRequests(pageable), page, size);
    }

    @PreAuthorize("hasRole('HR_MANAGER')")
    @PostMapping("/api/hr/loans/{id}/approve")
    public ResponseEntity<Map<String, Object>> approveLoan(@PathVariable Long id,
                                                            @RequestBody(required = false) Map<String, Object> body,
                                                            @AuthenticationPrincipal Jwt jwt) {
        String note = body != null ? (String) body.getOrDefault("hrNote", "") : "";
        return ok("Loan approved.", service.decideLoan(id, true, note, jwt.getSubject()));
    }

    @PreAuthorize("hasRole('HR_MANAGER')")
    @PostMapping("/api/hr/loans/{id}/reject")
    public ResponseEntity<Map<String, Object>> rejectLoan(@PathVariable Long id,
                                                           @RequestBody(required = false) Map<String, Object> body,
                                                           @AuthenticationPrincipal Jwt jwt) {
        String note = body != null ? (String) body.getOrDefault("hrNote", "") : "";
        return ok("Loan rejected.", service.decideLoan(id, false, note, jwt.getSubject()));
    }

    // ══════════════════════════════════════════════════════════════
    // AUTHORIZATION REQUESTS
    // ══════════════════════════════════════════════════════════════

    @PreAuthorize("hasAnyRole('EMPLOYEE','TEAM_LEADER','HR_MANAGER')")
    @PostMapping("/api/employee/authorizations")
    public ResponseEntity<Map<String, Object>> createAuthRequest(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        LocalDate start = body.get("startDate") != null ? LocalDate.parse((String) body.get("startDate")) : null;
        LocalDate end   = body.get("endDate")   != null ? LocalDate.parse((String) body.get("endDate"))   : null;
        var data = service.createAuthRequest(username,
                (String) body.get("authorizationType"), start, end, (String) body.get("reason"));
        return ok("Authorization request submitted.", data);
    }

    @PreAuthorize("hasAnyRole('EMPLOYEE','TEAM_LEADER','HR_MANAGER')")
    @GetMapping("/api/employee/authorizations")
    public ResponseEntity<Map<String, Object>> getMyAuths(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Map<String, Object>> list = service.getMyAuthRequests(jwt.getClaimAsString("preferred_username"), pageable);
        return okPage("My authorization requests.", list, page, size);
    }

    @PreAuthorize("hasRole('HR_MANAGER')")
    @GetMapping("/api/hr/authorizations")
    public ResponseEntity<Map<String, Object>> getAllAuths(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return okPage("All authorization requests.", service.getAllAuthRequests(pageable), page, size);
    }

    @PreAuthorize("hasRole('HR_MANAGER')")
    @PostMapping("/api/hr/authorizations/{id}/approve")
    public ResponseEntity<Map<String, Object>> approveAuth(@PathVariable Long id,
                                                            @RequestBody(required = false) Map<String, Object> body,
                                                            @AuthenticationPrincipal Jwt jwt) {
        String note = body != null ? (String) body.getOrDefault("hrNote", "") : "";
        return ok("Authorization approved.", service.decideAuth(id, true, note, jwt.getSubject()));
    }

    @PreAuthorize("hasRole('HR_MANAGER')")
    @PostMapping("/api/hr/authorizations/{id}/reject")
    public ResponseEntity<Map<String, Object>> rejectAuth(@PathVariable Long id,
                                                           @RequestBody(required = false) Map<String, Object> body,
                                                           @AuthenticationPrincipal Jwt jwt) {
        String note = body != null ? (String) body.getOrDefault("hrNote", "") : "";
        return ok("Authorization rejected.", service.decideAuth(id, false, note, jwt.getSubject()));
    }

    // ─── helper ──────────────────────────────────────────────────
    private ResponseEntity<Map<String, Object>> ok(String msg, Object data) {
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("message", msg);
        res.put("data", data);
        return ResponseEntity.ok(res);
    }

    private ResponseEntity<Map<String, Object>> okPage(String msg, Page<Map<String, Object>> pageData, int page, int size) {
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("message", msg);
        res.put("data", pageData.getContent());
        res.put("page", page);
        res.put("size", size);
        res.put("totalCount", pageData.getTotalElements());
        res.put("totalPages", pageData.getTotalPages());
        return ResponseEntity.ok(res);
    }
}
