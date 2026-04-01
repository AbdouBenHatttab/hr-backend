package tn.isetbizerte.pfe.hrbackend.infrastructure.reporting;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import jakarta.annotation.PostConstruct;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tn.isetbizerte.pfe.hrbackend.modules.employee.entity.LeaveRequest;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

@Service
public class LeaveReportService {

    private static final String TEMPLATE_PATH = "reports/leave/leave_approval.jrxml";
    private static final String LOGO_PATH = "static/images/logo.jpg";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // Base URL injected from application.properties — used to build QR verification link
    @Value("${app.base-url:http://localhost:8081}")
    private String baseUrl;

    @Value("${app.company.name:HR Management System}")
    private String companyName;

    // Compiled ONCE at startup — not on every request
    private JasperReport compiledReport;

    @PostConstruct
    public void init() {
        try (InputStream templateStream = new ClassPathResource(TEMPLATE_PATH).getInputStream()) {
            this.compiledReport = JasperCompileManager.compileReport(templateStream);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compile JasperReport template at startup", ex);
        }
    }

    public byte[] generateLeaveApprovalPdf(LeaveRequest leaveRequest) {
        try (InputStream logoStream = new ClassPathResource(LOGO_PATH).getInputStream()) {

            Map<String, Object> parameters = new HashMap<>();
            // Employee department — read from Person entity
            String dept = "";
            if (leaveRequest.getUser() != null && leaveRequest.getUser().getPerson() != null
                    && leaveRequest.getUser().getPerson().getDepartment() != null) {
                dept = leaveRequest.getUser().getPerson().getDepartment();
            }

            parameters.put("companyName",       companyName);
            parameters.put("employeeFullName",  leaveRequest.getEmployeeFullName());
            parameters.put("employeeEmail",     leaveRequest.getEmployeeEmail());
            parameters.put("employeeDepartment",dept);
            parameters.put("leaveType",         formatEnum(leaveRequest.getLeaveType().name()));
            parameters.put("leaveStartDate",    leaveRequest.getStartDate().format(DATE_FORMATTER));
            parameters.put("leaveEndDate",       leaveRequest.getEndDate().format(DATE_FORMATTER));
            parameters.put("numberOfDays",      leaveRequest.getNumberOfDays() + " calendar day(s)");
            parameters.put("reason",            leaveRequest.getReason() == null ? "N/A" : leaveRequest.getReason());
            parameters.put("requestDate",       leaveRequest.getRequestDate().format(DATETIME_FORMATTER));
            parameters.put("teamLeaderDecision",formatEnum(leaveRequest.getTeamLeaderDecision().name()));
            parameters.put("hrManagerDecision", formatEnum(leaveRequest.getHrDecision().name()));
            parameters.put("approvalDate",
                    leaveRequest.getApprovalDate() == null ? "N/A" : leaveRequest.getApprovalDate().format(DATE_FORMATTER));
            parameters.put("referenceNumber",   "LV-" + String.format("%06d", leaveRequest.getId()));
            parameters.put("logoStream", logoStream);

            // QR code — only embed if the document has a verification token
            if (leaveRequest.getVerificationToken() != null) {
                // QR points to frontend /verify/:token — shows a proper page when scanned
                String verifyUrl = baseUrl + "/verify/" + leaveRequest.getVerificationToken();
                InputStream qrStream = generateQrCodeStream(verifyUrl, 120);
                parameters.put("qrCodeStream", qrStream);
                parameters.put("verifyUrl", verifyUrl);
            }

            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(Collections.singletonList(leaveRequest));
            JasperPrint jasperPrint = JasperFillManager.fillReport(compiledReport, parameters, dataSource);
            return JasperExportManager.exportReportToPdf(jasperPrint);

        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate leave approval PDF", ex);
        }
    }

    /**
     * Generates a QR code image as an InputStream.
     * @param content the URL to encode
     * @param size    width and height in pixels
     */
    private InputStream generateQrCodeStream(String content, int size) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints);
            BufferedImage image = MatrixToImageWriter.toBufferedImage(matrix);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            return new ByteArrayInputStream(baos.toByteArray());
        } catch (WriterException | java.io.IOException e) {
            throw new IllegalStateException("Failed to generate QR code", e);
        }
    }

    /**
     * Converts enum name like "ANNUAL_LEAVE" → "Annual Leave"
     */
    private String formatEnum(String enumName) {
        if (enumName == null) return "N/A";
        String[] words = enumName.split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) result.append(" ");
            result.append(Character.toUpperCase(word.charAt(0)))
                  .append(word.substring(1).toLowerCase());
        }
        return result.toString();
    }
}



