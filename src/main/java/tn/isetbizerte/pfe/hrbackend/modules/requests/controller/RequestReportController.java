package tn.isetbizerte.pfe.hrbackend.modules.requests.controller;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import tn.isetbizerte.pfe.hrbackend.infrastructure.reporting.RequestReportService;
import tn.isetbizerte.pfe.hrbackend.modules.requests.service.RequestsService;

@RestController
@RequestMapping("/api/reports")
public class RequestReportController {

    private final RequestsService      service;
    private final RequestReportService reportService;

    public RequestReportController(RequestsService service, RequestReportService reportService) {
        this.service       = service;
        this.reportService = reportService;
    }

    @PreAuthorize("hasAnyRole('EMPLOYEE','TEAM_LEADER','HR_MANAGER')")
    @GetMapping("/documents/{id}")
    public ResponseEntity<byte[]> getDocumentPdf(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        var req  = service.getDocumentRequestForPdf(id, jwt.getSubject());
        var pdf  = reportService.generateDocumentPdf(req);
        return pdfResponse(pdf, "document_" + req.getDocumentType().name().toLowerCase() + "_" + id + ".pdf");
    }

    @PreAuthorize("hasAnyRole('EMPLOYEE','TEAM_LEADER','HR_MANAGER')")
    @GetMapping("/loans/{id}")
    public ResponseEntity<byte[]> getLoanPdf(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        var req = service.getLoanRequestForPdf(id, jwt.getSubject());
        var pdf = reportService.generateLoanPdf(req);
        return pdfResponse(pdf, "loan_" + id + ".pdf");
    }

    private ResponseEntity<byte[]> pdfResponse(byte[] pdf, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        return ResponseEntity.ok().headers(headers).body(pdf);
    }
}
