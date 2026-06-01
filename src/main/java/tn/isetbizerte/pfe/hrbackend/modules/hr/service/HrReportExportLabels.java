package tn.isetbizerte.pfe.hrbackend.modules.hr.service;

import tn.isetbizerte.pfe.hrbackend.modules.hr.dto.HrReportExportRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Provides pure label, date, and scalar formatting helpers for HR report exports.
 */
final class HrReportExportLabels {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private HrReportExportLabels() {
    }

    static String sheetKey(String value) {
        return value == null ? "" : value.trim()
                .replace(' ', '_')
                .replace('-', '_')
                .toUpperCase(Locale.ROOT);
    }

    static String displaySourceType(HrReportExportRequest.SourceType sourceType) {
        if (sourceType == null) {
            return "";
        }
        return switch (sourceType) {
            case LEAVE -> "Leave";
            case DOCUMENT -> "Document";
            case LOAN -> "Loan";
            case AUTHORIZATION -> "Authorization";
        };
    }

    static String displaySourceTypePlural(HrReportExportRequest.SourceType sourceType) {
        if (sourceType == null) {
            return "";
        }
        return switch (sourceType) {
            case LEAVE -> "Leaves";
            case DOCUMENT -> "Documents";
            case LOAN -> "Loans";
            case AUTHORIZATION -> "Authorizations";
        };
    }

    static String buildFilterSummary(HrReportExportRequest request) {
        List<String> parts = new ArrayList<>();
        if (request.getSourceTypes() != null && !request.getSourceTypes().isEmpty()) {
            parts.add("sourceTypes=" + request.getSourceTypes().stream().map(Enum::name).collect(Collectors.joining(",")));
        }
        if (request.getDateBasis() != null) {
            parts.add("dateBasis=" + request.getDateBasis().name());
        }
        if (request.getDateFrom() != null) {
            parts.add("dateFrom=" + request.getDateFrom());
        }
        if (request.getDateTo() != null) {
            parts.add("dateTo=" + request.getDateTo());
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            parts.add("status=" + request.getStatus());
        }
        if (request.getStatusGroup() != null) {
            parts.add("statusGroup=" + request.getStatusGroup().name());
        }
        if (request.getDepartmentId() != null) {
            parts.add("departmentId=" + request.getDepartmentId());
        }
        if (request.getTeamId() != null) {
            parts.add("teamId=" + request.getTeamId());
        }
        return parts.isEmpty() ? "None" : String.join(" · ", parts);
    }

    static String safe(String value) {
        String cleaned = HrReportExportSanitizer.cleanTextValue(value);
        return cleaned == null ? "" : cleaned;
    }

    static String safeString(String value) {
        return safe(value);
    }

    static String safeBoolean(Boolean value) {
        if (value == null) {
            return "";
        }
        return value ? "Yes" : "No";
    }

    static String formatDate(LocalDate date) {
        return date == null ? "" : date.format(DATE_FMT);
    }

    static String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null ? "" : dateTime.format(DATETIME_FMT);
    }

    static String formatTime(LocalTime time) {
        return time == null ? "" : time.format(TIME_FMT);
    }

    static Double averageLong(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return 0d;
        }
        long sum = 0L;
        for (Long value : values) {
            if (value != null) {
                sum += value;
            }
        }
        return sum / (double) values.size();
    }

    static int compareLongs(Long left, Long right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return Long.compare(left, right);
    }
}
