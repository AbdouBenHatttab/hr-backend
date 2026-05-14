package tn.isetbizerte.pfe.hrbackend.modules.hr.controller;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.isetbizerte.pfe.hrbackend.modules.hr.dto.HrReportExportRequest;
import tn.isetbizerte.pfe.hrbackend.modules.hr.service.HrReportExportService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/hr/reports")
public class HRReportExportController {

    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");

    private final HrReportExportService reportExportService;

    public HRReportExportController(HrReportExportService reportExportService) {
        this.reportExportService = reportExportService;
    }

    @PreAuthorize("hasRole('HR_MANAGER')")
    @PostMapping("/export/excel")
    public ResponseEntity<byte[]> exportExcel(@RequestBody HrReportExportRequest request,
                                              @AuthenticationPrincipal Jwt jwt) {
        byte[] content = reportExportService.exportExcel(request, jwt);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("arabsoft-hr-report-" + LocalDateTime.now().format(FILE_TS) + ".xlsx")
                .build());

        return ResponseEntity.ok().headers(headers).body(content);
    }
}
