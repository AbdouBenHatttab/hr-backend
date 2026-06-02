package tn.isetbizerte.pfe.hrbackend.infrastructure.reporting;

import jakarta.annotation.PostConstruct;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tn.isetbizerte.pfe.hrbackend.modules.employee.entity.LeaveRequest;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Service
public class LeaveReportService {

    private static final String TEMPLATE_PATH = "reports/leave/leave_approval.jrxml";
    private static final String LOGO_PATH = "static/images/logo.jpg";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Value("${app.company.name:HR Management System}")
    private String companyName;

    // The Jasper template is compiled once during service initialization.
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
            // Employee department - read from Person entity
            String dept = "";
            if (leaveRequest.getUser() != null && leaveRequest.getUser().getPerson() != null
                    && leaveRequest.getUser().getPerson().getDepartment() != null) {
                dept = leaveRequest.getUser().getPerson().getDepartment();
            }

            parameters.put("companyName",       companyName);
            parameters.put("employeeFullName",  leaveRequest.getEmployeeFullName());
            parameters.put("employeeEmail",     leaveRequest.getEmployeeEmail());
            parameters.put("employeeDepartment",dept);
            parameters.put("leaveType",         formatLeaveType(leaveRequest.getLeaveType().name()));
            parameters.put("leaveStartDate",    leaveRequest.getStartDate().format(DATE_FORMATTER));
            parameters.put("leaveEndDate",       leaveRequest.getEndDate().format(DATE_FORMATTER));
            parameters.put("numberOfDays",      formatWorkingDays(leaveRequest.getNumberOfDays()));
            parameters.put("reason",            leaveRequest.getReason() == null ? "N/A" : leaveRequest.getReason());
            parameters.put("requestDate",       leaveRequest.getRequestDate().format(DATETIME_FORMATTER));
            parameters.put("teamLeaderDecision",formatEnum(leaveRequest.getTeamLeaderDecision().name()));
            parameters.put("hrManagerDecision", formatEnum(leaveRequest.getHrDecision().name()));
            parameters.put("approvalDate",
                    leaveRequest.getApprovalDate() == null ? "N/A" : leaveRequest.getApprovalDate().format(DATE_FORMATTER));
            parameters.put("generatedOn",       LocalDateTime.now().format(DATETIME_FORMATTER));
            parameters.put("referenceNumber",   "LV-" + String.format("%06d", leaveRequest.getId()));
            parameters.put("logoStream", logoStream);

            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(Collections.singletonList(leaveRequest));
            JasperPrint jasperPrint = JasperFillManager.fillReport(compiledReport, parameters, dataSource);
            return JasperExportManager.exportReportToPdf(jasperPrint);

        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate leave approval PDF", ex);
        }
    }

    /**
     * Builds a readable leave-type label, e.g. "ANNUAL" -> "Annual Leave",
     * "SICK" -> "Sick Leave". Mirrors the wording used by the document PDF.
     */
    private String formatLeaveType(String enumName) {
        if (enumName == null) return "N/A";
        return formatEnum(enumName) + " Leave";
    }

    /**
     * Renders the approved duration with correct singular/plural wording,
     * e.g. 1 -> "1 working day", 6 -> "6 working days".
     */
    private String formatWorkingDays(Integer days) {
        if (days == null) return "N/A";
        return days + (days == 1 ? " working day" : " working days");
    }

    /**
     * Converts enum name like "ANNUAL_LEAVE" to "Annual Leave"
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



