package tn.isetbizerte.pfe.hrbackend.modules.hr.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;

/**
 * Applies text and cell sanitization rules before workbook values are exposed.
 */
final class HrReportExportSanitizer {

    private HrReportExportSanitizer() {
    }

    static String safeGroup(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    static String cleanProfileValue(String value) {
        return cleanTextValue(value);
    }

    static boolean isSentinelValue(String value) {
        if (value == null) {
            return true;
        }
        String cleaned = value.trim();
        if (cleaned.isBlank()) {
            return true;
        }
        String lower = cleaned.toLowerCase(Locale.ROOT);
        if (lower.equals("n/a")
                || lower.equals("na")
                || lower.equals("null")
                || lower.equals("none")
                || lower.equals("undefined")
                || lower.equals("unknown")
                || lower.equals("not specified")
                || lower.equals("not applicable")
                || lower.equals("-")
                || lower.equals("--")) {
            return true;
        }
        if (cleaned.matches("\\d{1,3}")) {
            return true;
        }
        return lower.contains("placeholder")
                || lower.contains("system evaluation")
                || lower.contains("system recommendation")
                || lower.contains("recommendation")
                || lower.contains("not yet assigned to a team")
                || lower.contains("team check skipped")
                || lower.contains("no issues found")
                || lower.contains("team nearing capacity")
                || lower.contains("workload manageable");
    }

    static String cleanWorkflowText(String value) {
        String cleaned = cleanTextValue(value);
        if (cleaned == null) {
            return null;
        }
        String lower = cleaned.toLowerCase(Locale.ROOT);
        if (lower.contains("not yet assigned to a team")
                || lower.contains("team check skipped")
                || lower.contains("no issues found")
                || lower.contains("team nearing capacity")
                || lower.contains("system evaluation")
                || lower.contains("system recommendation")
                || lower.contains("recommendation")
                || lower.contains("workload manageable")) {
            return null;
        }
        return cleaned;
    }

    static String cleanTextValue(String value) {
        if (isSentinelValue(value)) {
            return null;
        }
        return value.trim();
    }

    static void sanitizeDetailedRow(Row row, List<HrReportDetailColumnKind> columnKinds) {
        if (row == null || columnKinds == null || columnKinds.isEmpty()) {
            return;
        }
        for (int i = 0; i < columnKinds.size(); i++) {
            Cell cell = row.getCell(i);
            if (cell == null) {
                continue;
            }
            HrReportDetailColumnKind kind = columnKinds.get(i);
            if (kind == null) {
                continue;
            }
            switch (kind) {
                case TEXT -> sanitizeTextCell(cell);
                case DATE, DATETIME, TIME -> sanitizeTemporalCell(cell);
                case NUMBER, OPTIONAL_NUMBER -> sanitizeNumericCell(cell, kind == HrReportDetailColumnKind.OPTIONAL_NUMBER);
            }
        }
    }

    static void sanitizeTextCell(Cell cell) {
        if (cell == null) {
            return;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            cell.setBlank();
            return;
        }
        if (cell.getCellType() == CellType.STRING) {
            String value = cell.getStringCellValue();
            if (cleanTextValue(value) == null) {
                cell.setBlank();
            }
        }
    }

    static void sanitizeTemporalCell(Cell cell) {
        if (cell == null) {
            return;
        }
        if (cell.getCellType() == CellType.STRING) {
            if (cleanTextValue(cell.getStringCellValue()) == null) {
                cell.setBlank();
            }
            return;
        }
        if (cell.getCellType() == CellType.NUMERIC && cell.getNumericCellValue() < 1000d) {
            cell.setBlank();
        }
    }

    static void sanitizeNumericCell(Cell cell, boolean optional) {
        if (cell == null) {
            return;
        }
        if (cell.getCellType() == CellType.STRING) {
            if (cleanTextValue(cell.getStringCellValue()) == null) {
                cell.setBlank();
            }
            return;
        }
        if (cell.getCellType() == CellType.NUMERIC && optional) {
            return;
        }
    }

    static String sanitizeTextValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number || value instanceof LocalDate || value instanceof LocalDateTime || value instanceof LocalTime) {
            return null;
        }
        if (value instanceof Boolean) {
            return null;
        }
        if (value instanceof String text) {
            return cleanTextValue(text);
        }
        return cleanTextValue(String.valueOf(value));
    }
}
