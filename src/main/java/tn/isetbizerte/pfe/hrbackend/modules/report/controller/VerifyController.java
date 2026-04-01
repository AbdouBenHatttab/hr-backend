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

        Optional<AuthorizationRequest> authResult = authorizationRequestRepository.findByVerificationToken(token);
        if (authResult.isPresent()) {
            return ResponseEntity.ok(buildAuthorizationResponse(authResult.get()));
        }

        Map<String, Object> error = new LinkedHashMap<>();
        error.put("valid", false);
        error.put("message", "Document not found or token is invalid.");
        return ResponseEntity.status(404).body(error);
    }

    private Map<String, Object> buildLeaveResponse(LeaveRequest leave) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("valid", true);
        response.put("documentType", "LEAVE");
        response.put("employee",     leave.getEmployeeFullName());
        response.put("email",        leave.getEmployeeEmail());
        response.put("leaveType",    leave.getLeaveType().name());
        response.put("startDate",    leave.getStartDate().toString());
        response.put("endDate",      leave.getEndDate().toString());
        response.put("numberOfDays", leave.getNumberOfDays());
        response.put("status",       leave.getStatus().name());
        response.put("approvalDate", leave.getApprovalDate() != null ? leave.getApprovalDate().toString() : "N/A");
        response.put("message",      "This document is authentic and was issued by HR Nexus.");
        return response;
    }

    private Map<String, Object> buildDocumentResponse(DocumentRequest documentRequest) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("valid", true);
        response.put("documentType", "DOCUMENT");
        response.put("employee", documentRequest.getEmployeeFullName());
        response.put("email", documentRequest.getEmployeeEmail());
        response.put("requestType", documentRequest.getDocumentType().name());
        response.put("status", documentRequest.getStatus().name());
        response.put("processedAt", documentRequest.getProcessedAt() != null ? documentRequest.getProcessedAt().toString() : "N/A");
        response.put("message", "This document is authentic and was issued by HR Nexus.");
        return response;
    }

    private Map<String, Object> buildLoanResponse(LoanRequest loanRequest) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("valid", true);
        response.put("documentType", "LOAN");
        response.put("employee", loanRequest.getEmployeeFullName());
        response.put("email", loanRequest.getEmployeeEmail());
        response.put("loanType", loanRequest.getLoanType().name());
        response.put("amount", loanRequest.getAmount());
        response.put("repaymentMonths", loanRequest.getRepaymentMonths());
        response.put("status", loanRequest.getStatus().name());
        response.put("processedAt", loanRequest.getProcessedAt() != null ? loanRequest.getProcessedAt().toString() : "N/A");
        response.put("message", "This document is authentic and was issued by HR Nexus.");
        return response;
    }

    private Map<String, Object> buildAuthorizationResponse(AuthorizationRequest authorizationRequest) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("valid", true);
        response.put("documentType", "AUTHORIZATION");
        response.put("employee", authorizationRequest.getEmployeeFullName());
        response.put("email", authorizationRequest.getEmployeeEmail());
        response.put("authorizationType", authorizationRequest.getAuthorizationType().name());
        response.put("startDate", authorizationRequest.getStartDate() != null ? authorizationRequest.getStartDate().toString() : "N/A");
        response.put("endDate", authorizationRequest.getEndDate() != null ? authorizationRequest.getEndDate().toString() : "N/A");
        response.put("status", authorizationRequest.getStatus().name());
        response.put("processedAt", authorizationRequest.getProcessedAt() != null ? authorizationRequest.getProcessedAt().toString() : "N/A");
        response.put("message", "This document is authentic and was issued by HR Nexus.");
        return response;
    }
}
