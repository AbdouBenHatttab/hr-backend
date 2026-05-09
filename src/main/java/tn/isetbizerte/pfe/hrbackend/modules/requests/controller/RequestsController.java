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
import tn.isetbizerte.pfe.hrbackend.modules.requests.dto.ApproveLoanRequestDto;
import tn.isetbizerte.pfe.hrbackend.modules.requests.dto.CreateAuthorizationRequestDto;
import tn.isetbizerte.pfe.hrbackend.modules.requests.dto.CreateDocumentRequestDto;
import tn.isetbizerte.pfe.hrbackend.modules.requests.dto.CreateLoanRequestDto;
import tn.isetbizerte.pfe.hrbackend.modules.requests.dto.HrDecisionNoteDto;
import tn.isetbizerte.pfe.hrbackend.modules.requests.dto.RejectLoanRequestDto;
import tn.isetbizerte.pfe.hrbackend.modules.requests.dto.ScheduleLoanMeetingDto;
import tn.isetbizerte.pfe.hrbackend.modules.requests.dto.ValidateAuthorizationDraftRequestDto;
import tn.isetbizerte.pfe.hrbackend.modules.requests.dto.ValidateAuthorizationDraftResponseDto;
import tn.isetbizerte.pfe.hrbackend.modules.requests.dto.ValidateDocumentDraftRequestDto;
import tn.isetbizerte.pfe.hrbackend.modules.requests.dto.ValidateDocumentDraftResponseDto;
import tn.isetbizerte.pfe.hrbackend.modules.requests.dto.ValidateLoanDraftRequestDto;
import tn.isetbizerte.pfe.hrbackend.modules.requests.dto.ValidateLoanDraftResponseDto;
import tn.isetbizerte.pfe.hrbackend.modules.history.service.RequestHistoryService;
import tn.isetbizerte.pfe.hrbackend.modules.requests.service.RequestsService;

import java.util.HashMap;
import java.util.Map;

@RestController
public class RequestsController {

    private final RequestsService service;
    private final RequestHistoryService historyService;

    public RequestsController(RequestsService service, RequestHistoryService historyService) {
        this.service = service;
        this.historyService = historyService;
    }

    // DOCUMENT REQUESTS

    @PreAuthorize("hasAnyRole('EMPLOYEE','TEAM_LEADER','HR_MANAGER')")
    @PostMapping(RequestApiRoutes.EMPLOYEE_DOCUMENTS)
    public ResponseEntity<Map<String, Object>> createDocumentRequest(
            @Valid @RequestBody CreateDocumentRequestDto body,
            @AuthenticationPrincipal Jwt jwt) {
        var data = service.createDocumentRequest(jwt, body);
        return ok("Document request submitted.", data);
    }

    /**
     * POST /api/employee/documents/validate-draft
     *
     * Dry-run validation of a document draft. No data is saved, no events published.
     * Always returns HTTP 200; {@code valid=false} in the body signals a rule violation.
     * Called by React after FastAPI extracts structured fields from the user's chat message,
     * before showing the confirmation dialog.
     */
    @PreAuthorize("hasAnyRole('EMPLOYEE','TEAM_LEADER','HR_MANAGER')")
    @PostMapping(RequestApiRoutes.EMPLOYEE_DOCUMENTS_VALIDATE_DRAFT)
    public ResponseEntity<ValidateDocumentDraftResponseDto> validateDocumentDraft(
            @Valid @RequestBody ValidateDocumentDraftRequestDto body) {
        return ResponseEntity.ok(service.validateDocumentDraft(body));
    }

    @PreAuthorize("hasAnyRole('EMPLOYEE','TEAM_LEADER','HR_MANAGER')")
    @GetMapping(RequestApiRoutes.EMPLOYEE_DOCUMENTS)
    public ResponseEntity<Map<String, Object>> getMyDocuments(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Map<String, Object>> list = service.getMyDocumentRequests(jwt, pageable);
        return okPage("My document requests.", list, page, size);
    }

    @PreAuthorize("hasAnyRole('EMPLOYEE','TEAM_LEADER','HR_MANAGER')")
    @PostMapping(RequestApiRoutes.EMPLOYEE_DOCUMENTS_CANCEL)
    public ResponseEntity<Map<String, Object>> cancelMyDocument(@PathVariable Long id,
                                                                 @AuthenticationPrincipal Jwt jwt) {
        return ok("Document request canceled by employee.",
                service.cancelMyDocumentRequest(id, jwt.getSubject()));
    }

    @PreAuthorize("hasRole('HR_MANAGER')")
    @GetMapping(RequestApiRoutes.HR_DOCUMENTS)
    public ResponseEntity<Map<String, Object>> getAllDocuments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return okPage("All document requests.", service.getAllDocumentRequests(pageable), page, size);
    }

    @PreAuthorize("hasRole('HR_MANAGER')")
    @GetMapping(RequestApiRoutes.HR_DOCUMENTS_HISTORY)
    public ResponseEntity<Map<String, Object>> getDocumentHistory(@PathVariable Long id) {
        return ok("Document request history.", historyService.getHistory("DOCUMENT", id));
    }

    @PreAuthorize("hasRole('HR_MANAGER')")
    @PostMapping(RequestApiRoutes.HR_DOCUMENTS_APPROVE)
    public ResponseEntity<Map<String, Object>> approveDocument(@PathVariable Long id,
                                                                @RequestBody(required = false) HrDecisionNoteDto body,
                                                                @AuthenticationPrincipal Jwt jwt) {
        String note = body != null ? body.resolveHrNote() : "";
        return ok("Document request approved.", service.decideDocument(id, true, note, jwt.getSubject()));
    }

    @PreAuthorize("hasRole('HR_MANAGER')")
    @PostMapping(RequestApiRoutes.HR_DOCUMENTS_REJECT)
    public ResponseEntity<Map<String, Object>> rejectDocument(@PathVariable Long id,
                                                               @RequestBody(required = false) HrDecisionNoteDto body,
                                                               @AuthenticationPrincipal Jwt jwt) {
        String note = body != null ? body.resolveHrNote() : "";
        return ok("Document request rejected.", service.decideDocument(id, false, note, jwt.getSubject()));
    }

    @PreAuthorize("hasRole('HR_MANAGER')")
    @PostMapping(value = RequestApiRoutes.HR_DOCUMENTS_ATTACHMENT, consumes = "multipart/form-data")
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
    @GetMapping(RequestApiRoutes.EMPLOYEE_DOCUMENTS_ATTACHMENT)
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
    @PostMapping(value = RequestApiRoutes.HR_USER_DOCUMENTS, consumes = "multipart/form-data")
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
    @GetMapping(RequestApiRoutes.HR_USER_DOCUMENTS_LIST)
    public ResponseEntity<Map<String, Object>> getStoredEmployeeDocumentsForHr(@PathVariable Long userId) {
        return ok("Stored employee documents.", service.getStoredEmployeeDocumentsForHr(userId));
    }

    @PreAuthorize("hasAnyRole('EMPLOYEE','TEAM_LEADER','HR_MANAGER')")
    @GetMapping(RequestApiRoutes.EMPLOYEE_DOCUMENTS_MANAGED)
    public ResponseEntity<Map<String, Object>> getMyStoredEmployeeDocuments(@AuthenticationPrincipal Jwt jwt) {
        return ok("My stored employee documents.",
                service.getMyStoredEmployeeDocuments(jwt));
    }

    @PreAuthorize("hasAnyRole('EMPLOYEE','TEAM_LEADER','HR_MANAGER')")
    @GetMapping(RequestApiRoutes.EMPLOYEE_DOCUMENTS_MANAGED_DOWNLOAD)
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

    // LOAN REQUESTS

    @PreAuthorize("hasAnyRole('EMPLOYEE','TEAM_LEADER','HR_MANAGER')")
    @PostMapping(RequestApiRoutes.EMPLOYEE_LOANS)
    public ResponseEntity<Map<String, Object>> createLoanRequest(
            @Valid @RequestBody CreateLoanRequestDto body,
            @AuthenticationPrincipal Jwt jwt) {
        var data = service.createLoanRequest(jwt,
                body.getType(), body.getAmount(), body.getRepaymentMonths(), body.getReason());
        return ok("Loan request submitted.", data);
    }

    /**
     * POST /api/employee/loans/validate-draft
     *
     * Dry-run validation of a loan draft for the AI assistant.
     * No data is saved, no events published, no history recorded.
     * Always returns HTTP 200; {@code valid=false} signals a rule violation.
     *
     * repaymentMonths is optional so the assistant can mirror the manual loan
     * form, where HR confirms repayment terms later.
     */
    @PreAuthorize("hasAnyRole('EMPLOYEE','TEAM_LEADER','HR_MANAGER')")
    @PostMapping(RequestApiRoutes.EMPLOYEE_LOANS_VALIDATE_DRAFT)
    public ResponseEntity<ValidateLoanDraftResponseDto> validateLoanDraft(
            @Valid @RequestBody ValidateLoanDraftRequestDto body,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(service.validateLoanDraft(body, jwt));
    }

    @PreAuthorize("hasAnyRole('EMPLOYEE','TEAM_LEADER','HR_MANAGER')")
    @GetMapping(RequestApiRoutes.EMPLOYEE_LOANS)
    public ResponseEntity<Map<String, Object>> getMyLoans(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Map<String, Object>> list = service.getMyLoanRequests(jwt, pageable);
        return okPage("My loan requests.", list, page, size);
    }

    @PreAuthorize("hasAnyRole('EMPLOYEE','TEAM_LEADER','HR_MANAGER')")
    @PostMapping(RequestApiRoutes.EMPLOYEE_LOANS_CANCEL)
    public ResponseEntity<Map<String, Object>> cancelMyLoan(@PathVariable Long id,
                                                             @AuthenticationPrincipal Jwt jwt) {
        return ok("Loan request canceled by employee.",
                service.cancelMyLoanRequest(id, jwt.getSubject()));
    }

    @PreAuthorize("hasAnyRole('EMPLOYEE','TEAM_LEADER','HR_MANAGER')")
    @GetMapping(RequestApiRoutes.EMPLOYEE_LOANS_ELIGIBILITY)
    public ResponseEntity<Map<String, Object>> getLoanEligibility(@AuthenticationPrincipal Jwt jwt) {
        var data = service.getLoanEligibility(jwt);
        return ok("Loan eligibility info.", data);
    }

    @PreAuthorize("hasRole('HR_MANAGER')")
    @GetMapping(RequestApiRoutes.HR_LOANS)
    public ResponseEntity<Map<String, Object>> getAllLoans(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return okPage("All loan requests.", service.getAllLoanRequests(pageable), page, size);
    }

    @PreAuthorize("hasRole('HR_MANAGER')")
    @GetMapping(RequestApiRoutes.HR_LOANS_HISTORY)
    public ResponseEntity<Map<String, Object>> getLoanHistory(@PathVariable Long id) {
        return ok("Loan request history.", historyService.getHistory("LOAN", id));
    }

    @PreAuthorize("hasRole('HR_MANAGER')")
    @PostMapping(RequestApiRoutes.HR_LOANS_APPROVE)
    public ResponseEntity<Map<String, Object>> approveLoan(@PathVariable Long id,
                                                             @RequestBody(required = false) ApproveLoanRequestDto body,
                                                             @AuthenticationPrincipal Jwt jwt) {
        return ok("Loan approved.", service.decideLoan(
                id,
                true,
                body != null ? body.getHrNote() : "",
                body != null ? body.getRepaymentMonths() : null,
                body != null ? body.getApprovedAmount() : null,
                body != null ? body.resolveMonthlyPayback() : null,
                body != null ? body.getApprovedAmountJustification() : null,
                jwt.getSubject()
        ));
    }

    @PreAuthorize("hasRole('HR_MANAGER')")
    @PostMapping(RequestApiRoutes.HR_LOANS_REJECT)
    public ResponseEntity<Map<String, Object>> rejectLoan(@PathVariable Long id,
                                                           @RequestBody(required = false) RejectLoanRequestDto body,
                                                           @AuthenticationPrincipal Jwt jwt) {
        String note = body != null ? body.resolveNote() : "";
        return ok("Loan rejected.", service.decideLoan(id, false, note, null, null, null, jwt.getSubject()));
    }

    @PreAuthorize("hasRole('HR_MANAGER')")
    @PostMapping(RequestApiRoutes.HR_LOANS_SCHEDULE_MEETING)
    public ResponseEntity<Map<String, Object>> scheduleLoanMeeting(@PathVariable Long id,
                                                                    @RequestBody ScheduleLoanMeetingDto body,
                                                                    @AuthenticationPrincipal Jwt jwt) {
        return ok("Loan meeting scheduled.", service.scheduleLoanMeeting(
                id,
                body != null ? body.getMeetingAt() : null,
                body != null ? body.getMeetingNote() : null,
                jwt.getSubject()
        ));
    }

    @PreAuthorize("hasRole('HR_MANAGER')")
    @PostMapping(RequestApiRoutes.HR_LOANS_CANCEL_AFTER_MEETING)
    public ResponseEntity<Map<String, Object>> cancelLoanAfterMeeting(@PathVariable Long id,
                                                                      @Valid @RequestBody CancelLoanAfterMeetingDto body,
                                                                      @AuthenticationPrincipal Jwt jwt) {
        return ok("Loan canceled after meeting.",
                service.cancelLoanAfterMeeting(id, body.getReason(), jwt.getSubject()));
    }

    @PreAuthorize("hasRole('HR_MANAGER')")
    @PostMapping(value = RequestApiRoutes.HR_LOANS_ATTACHMENT, consumes = "multipart/form-data")
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
    @GetMapping(RequestApiRoutes.EMPLOYEE_LOANS_ATTACHMENT)
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

    // AUTHORIZATION REQUESTS

    /**
     * POST /api/employee/authorizations/validate-draft
     *
     * Dry-run validation of an authorization draft. No data is saved, no events
     * published, and no history records are created.
     * Always returns HTTP 200; {@code valid=false} in the body signals a rule violation.
     * Called by React/FastAPI after extracting structured fields from the user's chat
     * message, before showing the confirmation dialog.
     *
     * Requires authenticated user so that leave-overlap can be checked.
     */
    @PreAuthorize("hasAnyRole('EMPLOYEE','TEAM_LEADER','HR_MANAGER')")
    @PostMapping(RequestApiRoutes.EMPLOYEE_AUTHORIZATIONS_VALIDATE_DRAFT)
    public ResponseEntity<ValidateAuthorizationDraftResponseDto> validateAuthorizationDraft(
            @Valid @RequestBody ValidateAuthorizationDraftRequestDto body,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(service.validateAuthorizationDraft(body, jwt));
    }

    @PreAuthorize("hasAnyRole('EMPLOYEE','TEAM_LEADER','HR_MANAGER')")
    @PostMapping(RequestApiRoutes.EMPLOYEE_AUTHORIZATIONS)
    public ResponseEntity<Map<String, Object>> createAuthRequest(
            @Valid @RequestBody CreateAuthorizationRequestDto body,
            @AuthenticationPrincipal Jwt jwt) {
        var data = service.createAuthRequest(jwt, body);
        return ok("Authorization request submitted.", data);
    }

    @PreAuthorize("hasAnyRole('EMPLOYEE','TEAM_LEADER','HR_MANAGER')")
    @GetMapping(RequestApiRoutes.EMPLOYEE_AUTHORIZATIONS)
    public ResponseEntity<Map<String, Object>> getMyAuths(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Map<String, Object>> list = service.getMyAuthRequests(jwt, pageable);
        return okPage("My authorization requests.", list, page, size);
    }

    @PreAuthorize("hasAnyRole('EMPLOYEE','TEAM_LEADER','HR_MANAGER')")
    @PostMapping(RequestApiRoutes.EMPLOYEE_AUTHORIZATIONS_CANCEL)
    public ResponseEntity<Map<String, Object>> cancelMyAuth(@PathVariable Long id,
                                                             @AuthenticationPrincipal Jwt jwt) {
        return ok("Authorization request canceled by employee.",
                service.cancelMyAuthRequest(id, jwt.getSubject()));
    }

    @PreAuthorize("hasRole('HR_MANAGER')")
    @GetMapping(RequestApiRoutes.HR_AUTHORIZATIONS)
    public ResponseEntity<Map<String, Object>> getAllAuths(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return okPage("All authorization requests.", service.getAllAuthRequests(pageable), page, size);
    }

    @PreAuthorize("hasRole('HR_MANAGER')")
    @GetMapping(RequestApiRoutes.HR_AUTHORIZATIONS_HISTORY)
    public ResponseEntity<Map<String, Object>> getAuthorizationHistory(@PathVariable Long id) {
        return ok("Authorization request history.", historyService.getHistory("AUTH", id));
    }

    @PreAuthorize("hasRole('HR_MANAGER')")
    @PostMapping(RequestApiRoutes.HR_AUTHORIZATIONS_APPROVE)
    public ResponseEntity<Map<String, Object>> approveAuth(@PathVariable Long id,
                                                            @RequestBody(required = false) HrDecisionNoteDto body,
                                                            @AuthenticationPrincipal Jwt jwt) {
        String note = body != null ? body.resolveHrNote() : "";
        return ok("Authorization approved.", service.decideAuth(id, true, note, jwt.getSubject()));
    }

    @PreAuthorize("hasRole('HR_MANAGER')")
    @PostMapping(RequestApiRoutes.HR_AUTHORIZATIONS_REJECT)
    public ResponseEntity<Map<String, Object>> rejectAuth(@PathVariable Long id,
                                                           @RequestBody(required = false) HrDecisionNoteDto body,
                                                           @AuthenticationPrincipal Jwt jwt) {
        String note = body != null ? body.resolveHrNote() : "";
        return ok("Authorization rejected.", service.decideAuth(id, false, note, jwt.getSubject()));
    }

    // helper
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
