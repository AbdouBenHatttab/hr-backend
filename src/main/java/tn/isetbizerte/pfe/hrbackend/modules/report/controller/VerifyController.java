package tn.isetbizerte.pfe.hrbackend.modules.report.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.isetbizerte.pfe.hrbackend.modules.requests.entity.AuthorizationRequest;
import tn.isetbizerte.pfe.hrbackend.modules.requests.entity.DocumentRequest;
import tn.isetbizerte.pfe.hrbackend.modules.requests.entity.LoanRequest;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.AuthorizationRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.DocumentRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.LoanRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.employee.entity.LeaveRequest;
import tn.isetbizerte.pfe.hrbackend.modules.employee.repository.LeaveRequestRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Public endpoint — no authentication required.
 * Anyone who scans the QR code on a PDF can verify the document.
 *
 * GET /public/verify/{token}
 */
@RestController
@RequestMapping("/public/verify")
public class VerifyController {

    private final LeaveRequestRepository leaveRequestRepository;
    private final DocumentRequestRepository documentRequestRepository;
    private final LoanRequestRepository loanRequestRepository;
    private final AuthorizationRequestRepository authorizationRequestRepository;

    public VerifyController(LeaveRequestRepository leaveRequestRepository,
                            DocumentRequestRepository documentRequestRepository,
                            LoanRequestRepository loanRequestRepository,
                            AuthorizationRequestRepository authorizationRequestRepository) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.documentRequestRepository = documentRequestRepository;
        this.loanRequestRepository = loanRequestRepository;
        this.authorizationRequestRepository = authorizationRequestRepository;
    }

    @GetMapping("/{token}")
    public ResponseEntity<Map<String, Object>> verifyDocument(@PathVariable String token) {
        Optional<LeaveRequest> leaveResult = leaveRequestRepository.findByVerificationToken(token);
        if (leaveResult.isPresent()) {
            return ResponseEntity.ok(buildLeaveResponse(leaveResult.get()));
        }

        Optional<DocumentRequest> documentResult = documentRequestRepository.findByVerificationToken(token);
        if (documentResult.isPresent()) {
            return ResponseEntity.ok(buildDocumentResponse(documentResult.get()));
        }

        Optional<LoanRequest> loanResult = loanRequestRepository.findByVerificationToken(token);
        if (loanResult.isPresent()) {
            return ResponseEntity.ok(buildLoanResponse(loanResult.get()));
        }

        Optional<AuthorizationRequest> authorizationResult = authorizationRequestRepository.findByVerificationToken(token);
        if (authorizationResult.isPresent()) {
            return ResponseEntity.ok(buildAuthorizationResponse(authorizationResult.get()));
        }

        Map<String, Object> error = new LinkedHashMap<>();
        error.put("valid", false);
        error.put("message", "Document not found or token is invalid.");
        return ResponseEntity.status(404).body(error);
    }

    private Map<String, Object> buildLeaveResponse(LeaveRequest leave) {
        return buildSafeResponse(
                "LEAVE",
                leave.getStatus() != null ? leave.getStatus().name() : null,
                leave.getApprovalDate()
        );
    }

    private Map<String, Object> buildDocumentResponse(DocumentRequest documentRequest) {
        return buildSafeResponse(
                "DOCUMENT",
                documentRequest.getStatus() != null ? documentRequest.getStatus().name() : null,
                documentRequest.getProcessedAt()
        );
    }

    private Map<String, Object> buildLoanResponse(LoanRequest loanRequest) {
        return buildSafeResponse(
                "LOAN",
                loanRequest.getStatus() != null ? loanRequest.getStatus().name() : null,
                loanRequest.getProcessedAt()
        );
    }

    private Map<String, Object> buildAuthorizationResponse(AuthorizationRequest authorizationRequest) {
        return buildSafeResponse(
                "AUTHORIZATION",
                authorizationRequest.getStatus() != null ? authorizationRequest.getStatus().name() : null,
                authorizationRequest.getProcessedAt()
        );
    }

    private Map<String, Object> buildSafeResponse(String documentType, String status, LocalDate issuedOn) {
        return buildSafeResponse(documentType, status, issuedOn != null ? issuedOn.toString() : null);
    }

    private Map<String, Object> buildSafeResponse(String documentType, String status, LocalDateTime issuedAt) {
        return buildSafeResponse(documentType, status, issuedAt != null ? issuedAt.toLocalDate().toString() : null);
    }

    private Map<String, Object> buildSafeResponse(String documentType, String status, String issuedOn) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("valid", true);
        response.put("documentType", documentType);
        response.put("status", status != null ? status : "ISSUED");
        response.put("issuedOn", issuedOn != null ? issuedOn : "N/A");
        response.put("issuer", "ArabSoft Human Resources Management System");
        response.put("message", "This document is authentic and was issued by ArabSoft.");
        return response;
    }

}
