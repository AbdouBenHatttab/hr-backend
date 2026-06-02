package tn.isetbizerte.pfe.hrbackend.infrastructure.reporting;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import jakarta.annotation.PostConstruct;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tn.isetbizerte.pfe.hrbackend.modules.employee.entity.EmployeeLeaveBalance;
import tn.isetbizerte.pfe.hrbackend.modules.requests.entity.DocumentRequest;
import tn.isetbizerte.pfe.hrbackend.modules.requests.entity.LoanRequest;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RequestReportService {

    private static final String LOGO_PATH = "static/images/logo.jpg";
    private static final DateTimeFormatter DATE_FMT     = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Value("${app.base-url:http://localhost:5173}")
    private String baseUrl;

    @Value("${app.company.name:ArabSoft}")
    private String companyName;

    private JasperReport documentReport;
    private JasperReport loanReport;
    @PostConstruct
    public void init() {
        documentReport          = compile("reports/leave/document_request.jrxml");
        loanReport              = compile("reports/leave/loan_request.jrxml");
    }

    // DOCUMENT PDF
    public byte[] generateDocumentPdf(DocumentRequest req) {
        return generateDocumentPdf(req, null);
    }

    /**
     * Renders the Leave Balance Statement PDF. The {@code balance} carries the current
     * leave balance values for the request employee at generation time; when null
     * (no managed balance row), the summary falls back to "N/A" without failing.
     */
    public byte[] generateDocumentPdf(DocumentRequest req, EmployeeLeaveBalance balance) {
        try (InputStream logo = new ClassPathResource(LOGO_PATH).getInputStream()) {
            Map<String, Object> p = new HashMap<>();
            p.put("companyName",        companyName);
            p.put("referenceNumber",    "DOC-" + String.format("%06d", req.getId()));
            p.put("employeeFullName",   req.getEmployeeFullName());
            p.put("employeeEmail",      req.getEmployeeEmail());
            p.put("employeeDepartment", req.getEmployeeDepartment());
            p.put("documentType",       fmt(req.getDocumentType().name()));
            p.put("notes",              req.getNotes() != null ? req.getNotes() : "");
            p.put("requestedAt",        req.getRequestedAt() != null ? req.getRequestedAt().format(DATETIME_FMT) : "");
            p.put("processedAt",        req.getProcessedAt() != null ? req.getProcessedAt().format(DATETIME_FMT) : "");
            p.put("hrNote",             req.getHrNote() != null ? req.getHrNote() : "");
            p.put("logoStream",         logo);

            boolean hasBalance = balance != null;
            p.put("balanceYear",        hasBalance ? String.valueOf(balance.getYear()) : String.valueOf(LocalDate.now().getYear()));
            p.put("balanceLeaveType",   hasBalance ? leaveTypeLabel(balance.getLeaveType().name()) : "Annual Leave");
            p.put("allocatedDays",      hasBalance ? formatDays(balance.getAllocatedDays())   : "N/A");
            p.put("carryForwardDays",   hasBalance ? formatDays(balance.getCarryForwardDays()) : "N/A");
            p.put("usedDays",           hasBalance ? formatDays(balance.getUsedDays())        : "N/A");
            p.put("reservedDays",       hasBalance ? formatDays(balance.getReservedDays())    : "N/A");
            p.put("availableDays",      hasBalance ? formatDays(balance.getAvailableDays())   : "N/A");
            p.put("generatedOn",        LocalDateTime.now().format(DATETIME_FMT));

            return fill(documentReport, p);
        } catch (Exception e) { throw new IllegalStateException("Failed to generate document PDF", e); }
    }

    // LOAN PDF
    public byte[] generateLoanPdf(LoanRequest req) {
        try (InputStream logo = new ClassPathResource(LOGO_PATH).getInputStream()) {
            Map<String, Object> p = new HashMap<>();
            p.put("companyName",        companyName);
            p.put("referenceNumber",    "LOAN-" + String.format("%06d", req.getId()));
            p.put("employeeFullName",   req.getEmployeeFullName());
            p.put("employeeEmail",      req.getEmployeeEmail());
            p.put("employeeDepartment", req.getEmployeeDepartment());
            p.put("loanType",           fmt(req.getLoanType().name()));
            p.put("amount",             req.getAmount().toPlainString() + " TND");
            p.put("repaymentMonths",    req.getRepaymentMonths() != null ? String.valueOf(req.getRepaymentMonths()) : null);
            p.put("reason",             req.getReason() != null ? req.getReason() : "");
            p.put("requestedAt",        req.getRequestedAt() != null ? req.getRequestedAt().format(DATETIME_FMT) : "");
            p.put("processedAt",        req.getProcessedAt() != null ? req.getProcessedAt().format(DATETIME_FMT) : "");
            p.put("hrNote",             req.getHrNote() != null ? req.getHrNote() : "");
            p.put("logoStream",         logo);
            if (req.getVerificationToken() != null) {
                String url = baseUrl + "/verify/" + req.getVerificationToken();
                p.put("qrCodeStream", qr(url));
            }
            return fill(loanReport, p);
        } catch (Exception e) { throw new IllegalStateException("Failed to generate loan PDF", e); }
    }

    // HELPERS
    private JasperReport compile(String path) {
        try (InputStream is = new ClassPathResource(path).getInputStream()) {
            return JasperCompileManager.compileReport(is);
        } catch (Exception e) { throw new IllegalStateException("Failed to compile " + path, e); }
    }

    private byte[] fill(JasperReport report, Map<String, Object> params) throws JRException {
        JasperPrint print = JasperFillManager.fillReport(report, params,
                new JRBeanCollectionDataSource(Collections.singletonList(new Object())));
        return JasperExportManager.exportReportToPdf(print);
    }

    private InputStream qr(String url) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix matrix = writer.encode(url, BarcodeFormat.QR_CODE, 120, 120, hints);
            BufferedImage img = MatrixToImageWriter.toBufferedImage(matrix);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "PNG", baos);
            return new ByteArrayInputStream(baos.toByteArray());
        } catch (Exception e) { throw new IllegalStateException("QR generation failed", e); }
    }

    private String fmt(String enumName) {
        if (enumName == null) return "";
        return Arrays.stream(enumName.split("_"))
                .map(w -> w.charAt(0) + w.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }

    private String leaveTypeLabel(String enumName) {
        return fmt(enumName) + " Leave";
    }

    private String formatDays(BigDecimal value) {
        if (value == null) return "N/A";
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString() + " days";
    }

    public static String documentReference(Long requestId) {
        return "DOC-" + String.format("%06d", requestId);
    }
}
