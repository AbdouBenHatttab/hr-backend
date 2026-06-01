package tn.isetbizerte.pfe.hrbackend.modules.hr.service;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;

/**
 * Creates the Apache POI styles used by the HR report workbook.
 */
final class HrReportExcelStyleHelper {

    private HrReportExcelStyleHelper() {
    }

    static HrReportWorkbookStyles createWorkbookStyles(Workbook workbook) {
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 16);
        titleFont.setColor(IndexedColors.WHITE.getIndex());

        Font subtitleFont = workbook.createFont();
        subtitleFont.setItalic(true);
        subtitleFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());

        Font sectionFont = workbook.createFont();
        sectionFont.setBold(true);
        sectionFont.setColor(IndexedColors.WHITE.getIndex());

        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());

        Font labelFont = workbook.createFont();
        labelFont.setBold(true);

        CellStyle titleStyle = workbook.createCellStyle();
        titleStyle.setFont(titleFont);
        titleStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        titleStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        titleStyle.setAlignment(HorizontalAlignment.LEFT);
        titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        titleStyle.setWrapText(true);

        CellStyle subtitleStyle = workbook.createCellStyle();
        subtitleStyle.setFont(subtitleFont);
        subtitleStyle.setWrapText(true);

        CellStyle sectionHeaderStyle = workbook.createCellStyle();
        sectionHeaderStyle.setFont(sectionFont);
        sectionHeaderStyle.setFillForegroundColor(IndexedColors.BLUE_GREY.getIndex());
        sectionHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        sectionHeaderStyle.setAlignment(HorizontalAlignment.LEFT);

        CellStyle tableHeaderStyle = workbook.createCellStyle();
        tableHeaderStyle.setFont(headerFont);
        tableHeaderStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        tableHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        tableHeaderStyle.setBorderBottom(BorderStyle.THIN);
        tableHeaderStyle.setBorderTop(BorderStyle.THIN);
        tableHeaderStyle.setBorderLeft(BorderStyle.THIN);
        tableHeaderStyle.setBorderRight(BorderStyle.THIN);
        tableHeaderStyle.setAlignment(HorizontalAlignment.CENTER);
        tableHeaderStyle.setVerticalAlignment(VerticalAlignment.CENTER);

        CellStyle sectionLabelStyle = workbook.createCellStyle();
        sectionLabelStyle.setFont(labelFont);
        sectionLabelStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        sectionLabelStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        sectionLabelStyle.setBorderBottom(BorderStyle.THIN);

        CellStyle textStyle = workbook.createCellStyle();
        textStyle.setWrapText(false);
        textStyle.setBorderBottom(BorderStyle.THIN);
        textStyle.setBorderTop(BorderStyle.THIN);
        textStyle.setBorderLeft(BorderStyle.THIN);
        textStyle.setBorderRight(BorderStyle.THIN);

        CellStyle wrapTextStyle = workbook.createCellStyle();
        wrapTextStyle.setWrapText(true);
        wrapTextStyle.setBorderBottom(BorderStyle.THIN);
        wrapTextStyle.setBorderTop(BorderStyle.THIN);
        wrapTextStyle.setBorderLeft(BorderStyle.THIN);
        wrapTextStyle.setBorderRight(BorderStyle.THIN);

        CellStyle dateStyle = workbook.createCellStyle();
        dateStyle.setDataFormat(workbook.createDataFormat().getFormat("dd/mm/yyyy"));
        dateStyle.setBorderBottom(BorderStyle.THIN);
        dateStyle.setBorderTop(BorderStyle.THIN);
        dateStyle.setBorderLeft(BorderStyle.THIN);
        dateStyle.setBorderRight(BorderStyle.THIN);

        CellStyle dateTimeStyle = workbook.createCellStyle();
        dateTimeStyle.setDataFormat(workbook.createDataFormat().getFormat("dd/mm/yyyy hh:mm"));
        dateTimeStyle.setBorderBottom(BorderStyle.THIN);
        dateTimeStyle.setBorderTop(BorderStyle.THIN);
        dateTimeStyle.setBorderLeft(BorderStyle.THIN);
        dateTimeStyle.setBorderRight(BorderStyle.THIN);

        CellStyle timeStyle = workbook.createCellStyle();
        timeStyle.setDataFormat(workbook.createDataFormat().getFormat("hh:mm"));
        timeStyle.setBorderBottom(BorderStyle.THIN);
        timeStyle.setBorderTop(BorderStyle.THIN);
        timeStyle.setBorderLeft(BorderStyle.THIN);
        timeStyle.setBorderRight(BorderStyle.THIN);

        CellStyle integerStyle = workbook.createCellStyle();
        integerStyle.setDataFormat(workbook.createDataFormat().getFormat("0"));
        integerStyle.setBorderBottom(BorderStyle.THIN);
        integerStyle.setBorderTop(BorderStyle.THIN);
        integerStyle.setBorderLeft(BorderStyle.THIN);
        integerStyle.setBorderRight(BorderStyle.THIN);

        CellStyle decimalStyle = workbook.createCellStyle();
        decimalStyle.setDataFormat(workbook.createDataFormat().getFormat("0.00"));
        decimalStyle.setBorderBottom(BorderStyle.THIN);
        decimalStyle.setBorderTop(BorderStyle.THIN);
        decimalStyle.setBorderLeft(BorderStyle.THIN);
        decimalStyle.setBorderRight(BorderStyle.THIN);

        return new HrReportWorkbookStyles(titleStyle, subtitleStyle, sectionHeaderStyle, tableHeaderStyle,
                sectionLabelStyle, textStyle, wrapTextStyle, dateStyle, dateTimeStyle, timeStyle, integerStyle, decimalStyle);
    }

    static CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    static CellStyle boldStyle(Workbook workbook, int fontSize, boolean underline) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) fontSize);
        font.setUnderline(underline ? Font.U_SINGLE : Font.U_NONE);
        style.setFont(font);
        return style;
    }
}
