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
import org.springframework.web.multipart.MultipartFile;
import tn.isetbizerte.pfe.hrbackend.modules.requests.dto.CancelLoanAfterMeetingDto;
import tn.isetbizerte.pfe.hrbackend.modules.requests.dto.CreateAuthorizationRequestDto;
import tn.isetbizerte.pfe.hrbackend.modules.requests.dto.CreateDocumentRequestDto;
import tn.isetbizerte.pfe.hrbackend.modules.requests.dto.CreateLoanRequestDto;
import tn.isetbizerte.pfe.hrbackend.modules.requests.service.RequestsService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
            @Valid @RequestBody CreateDocumentRequestDto body,
            @AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        var data = service.createDocumentRequest(username, body);
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

    @PreAuthorize("hasAnyRole('EMPLOYEE','TEAM_LEADER','HR_MANAGER')")
    @PostMapping("/api/employee/documents/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancelMyDocument(@PathVariable Long id,
                                                                 @AuthenticationPrincipal Jwt jwt) {
        return ok("Document request canceled by employee.",
                service.cancelMyDocumentRequest(id, jwt.getSubject()));
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

    @PreAuthorize("hasRole('HR_MANAGER')")
    @PostMapping(value = "/api/hr/documents/{id}/attachment", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, Object>> uploadDocumentAttachment(@PathVariable Long id,
                                                                        @RequestParam("file") MultipartFile file,
                                                                        @AuthenticationPrincipal Jwt jwt) throws Exception {
        return ok("Attachment uploaded.",
                service.uploadDocumentAttachment(
                        id,
                        jwt.getSubject(),
                        file != null ? file.getOriginalFilename() : null,
                        file != null ? file.getContentType() : null,
                        file != null ? file.getBytes() : null
                ));
    }

    @PreAuthorize("hasAnyRole('EMPLOYEE','TEAM_LEADER','HR_MANAGER')")
    @GetMapping("/api/employee/documents/{id}/attachment")
    public ResponseEntity<byte[]> downloadDocumentAttachment(@PathVariable Long id,
                                                             @AuthenticationPrincipal Jwt jwt) {
        var req = service.getDocumentRequestForAttachment(id, jwt.getSubject());
        byte[] bytes = service.readDocumentAttachment(req);

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        String ct = req.getAttachmentContentType() != null ? req.getAttachmentContentType() : "application/octet-stream";
        try {
            headers.setContentType(org.springframework.http.MediaType.parseMediaType(ct));
        } catch (Exception e) {
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM);
        }
        headers.setContentDisposition(org.springframework.http.ContentDisposition.attachment()
                .filename(req.getAttachmentFileName() != null ? req.getAttachmentFileName() : ("document_attachment_" + id))
                .build());
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    @PreAuthorize("hasRole('HR_MANAGER')")
    @PostMapping(value = "/api/hr/users/{userId}/documents", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, Object>> uploadStoredEmployeeDocument(@PathVariable Long userId,
                                                                            @RequestParam(value = "documentType", defaultValue = "CONTRACT_COPY") String documentType,
                                                                            @RequestParam(value = "note", required = false) String note,
                                                                            @RequestParam("file") MultipartFile file,
                                                                            @AuthenticationPrincipal Jwt jwt) throws Exception {
        return ok("Employee document stored.",
                service.uploadStoredEmployeeDocument(
                        userId,
                        documentType,
                        note,
                        jwt.getSubject(),
                        file != null ? file.getOriginalFilename() : null,
                        file != null ? file.getContentType() : null,
                        file != null ? file.getBytes() : null
                ));
    }

    @PreAuthorize("hasRole('HR_MANAGER')")
    @GetMapping("/api/hr/users/{userId}/documents")
    public ResponseEntity<Map<String, Object>> getStoredEmployeeDocumentsForHr(@PathVariable Long userId) {
        return ok("Stored employee documents.", service.getStoredEmployeeDocumentsForHr(userId));
    }

    @PreAuthorize("hasAnyRole('EMPLOYEE','TEAM_LEADER','HR_MANAGER')")
    @GetMapping("/api/employee/documents/managed")
    public ResponseEntity<Map<String, Object>> getMyStoredEmployeeDocuments(@AuthenticationPrincipal Jwt jwt) {
        return ok("My stored employee documents.",
                service.getMyStoredEmployeeDocuments(jwt.getClaimAsString("preferred_username")));
    }

    @PreAuthorize("hasAnyRole('EMPLOYEE','TEAM_LEADER','HR_MANAGER')")
    @GetMapping("/api/employee/documents/managed/{id}/download")
    public ResponseEntity<byte[]> downloadStoredEmployeeDocument(@PathVariable Long id,
                                                                 @AuthenticationPrincipal Jwt jwt) {
        var doc = service.getStoredEmployeeDocumentForDownload(id, jwt.getSubject());
        byte[] bytes = service.readStoredEmployeeDocument(doc);

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        String ct = doc.getContentType() != null ? doc.getContentType() : "application/octet-stream";
        try {
            headers.setContentType(org.springframework.http.MediaType.parseMediaType(ct));
        } catch (Exception e) {
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM);
        }
        headers.setContentDisposition(org.springframework.http.ContentDisposition.attachment()
                .filename(doc.getFileName() != null ? doc.getFileName() : ("employee_document_" + id))
                .build());
        return ResponseEntity.ok().headers(headers).body(bytes);
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
    @PostMapping("/api/employee/loans/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancelMyLoan(@PathVariable Long id,
                                                             @AuthenticationPrincipal Jwt jwt) {
        return ok("Loan request canceled by employee.",
                service.cancelMyLoanRequest(id, jwt.getSubject()));
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
        String note = "";
        if (body != null) {
            Object value = body.getOrDefault("hrDecisionReason",
                    body.getOrDefault("reason", body.getOrDefault("hrNote", "")));
            note = value != null ? value.toString() : "";
        }
        return ok("Loan rejected.", service.decideLoan(id, false, note, jwt.getSubject()));
    }

    @PreAuthorize("hasRole('HR_MANAGER')")
    @PostMapping("/api/hr/loans/{id}/schedule-meeting")
    public ResponseEntity<Map<String, Object>> scheduleLoanMeeting(@PathVariable Long id,
                                                                    @RequestBody Map<String, Object> body,
                                                                    @AuthenticationPrincipal Jwt jwt) {
        LocalDateTime meetingAt;
        Object meetingAtValue = body.get("meetingAt");
        if (meetingAtValue != null && !meetingAtValue.toString().isBlank()) {
            meetingAt = LocalDateTime.parse(meetingAtValue.toString());
        } else {
            LocalDate date = LocalDate.parse((String) body.get("meetingDate"));
            LocalTime time = LocalTime.parse((String) body.get("meetingTime"));
            meetingAt = LocalDateTime.of(date, time);
        }
        String note = body.get("meetingNote") != null ? body.get("meetingNote").toString() : "";
        return ok("Loan meeting scheduled.", service.scheduleLoanMeeting(id, meetingAt, note, jwt.getSubject()));
    }

    @PreAuthorize("hasRole('HR_MANAGER')")
    @PostMapping("/api/hr/loans/{id}/cancel-after-meeting")
    public ResponseEntity<Map<String, Object>> cancelLoanAfterMeeting(@PathVariable Long id,
                                                                      @Valid @RequestBody CancelLoanAfterMeetingDto body,
                                                                      @AuthenticationPrincipal Jwt jwt) {
        return ok("Loan canceled after meeting.",
                service.cancelLoanAfterMeeting(id, body.getReason(), jwt.getSubject()));
    }

    @PreAuthorize("hasRole('HR_MANAGER')")
    @PostMapping(value = "/api/hr/loans/{id}/attachment", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, Object>> uploadLoanAttachment(@PathVariable Long id,
                                                                    @RequestParam("file") MultipartFile file,
                                                                    @AuthenticationPrincipal Jwt jwt) throws Exception {
        return ok("Loan final file uploaded.",
                service.uploadLoanAttachment(
                        id,
                        jwt.getSubject(),
                        file != null ? file.getOriginalFilename() : null,
                        file != null ? file.getContentType() : null,
                        file != null ? file.getBytes() : null
                ));
    }

    @PreAuthorize("hasAnyRole('EMPLOYEE','TEAM_LEADER','HR_MANAGER')")
    @GetMapping("/api/employee/loans/{id}/attachment")
    public ResponseEntity<byte[]> downloadLoanAttachment(@PathVariable Long id,
                                                         @AuthenticationPrincipal Jwt jwt) {
        var req = service.getLoanRequestForAttachment(id, jwt.getSubject());
        byte[] bytes = service.readLoanAttachment(req);

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        String ct = req.getAttachmentContentType() != null ? req.getAttachmentContentType() : "application/octet-stream";
        try {
            headers.setContentType(org.springframework.http.MediaType.parseMediaType(ct));
        } catch (Exception e) {
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM);
        }
        headers.setContentDisposition(org.springframework.http.ContentDisposition.attachment()
                .filename(req.getAttachmentFileName() != null ? req.getAttachmentFileName() : ("loan_attachment_" + id))
                .build());
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    // ══════════════════════════════════════════════════════════════
    // AUTHORIZATION REQUESTS
    // ══════════════════════════════════════════════════════════════

    @PreAuthorize("hasAnyRole('EMPLOYEE','TEAM_LEADER','HR_MANAGER')")
    @PostMapping("/api/employee/authorizations")
    public ResponseEntity<Map<String, Object>> createAuthRequest(
            @Valid @RequestBody CreateAuthorizationRequestDto body,
            @AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        var data = service.createAuthRequest(username, body);
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

    @PreAuthorize("hasAnyRole('EMPLOYEE','TEAM_LEADER','HR_MANAGER')")
    @PostMapping("/api/employee/authorizations/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancelMyAuth(@PathVariable Long id,
                                                             @AuthenticationPrincipal Jwt jwt) {
        return ok("Authorization request canceled by employee.",
                service.cancelMyAuthRequest(id, jwt.getSubject()));
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
