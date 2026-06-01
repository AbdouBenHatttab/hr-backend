package tn.isetbizerte.pfe.hrbackend.modules.hr.service;

import org.apache.poi.ss.usermodel.CellStyle;

/**
 * Groups workbook cell styles so report sheets share one style bundle.
 */
record HrReportWorkbookStyles(CellStyle titleStyle,
                              CellStyle subtitleStyle,
                              CellStyle sectionHeaderStyle,
                              CellStyle tableHeaderStyle,
                              CellStyle sectionLabelStyle,
                              CellStyle textStyle,
                              CellStyle wrapTextStyle,
                              CellStyle dateStyle,
                              CellStyle dateTimeStyle,
                              CellStyle timeStyle,
                              CellStyle integerStyle,
                              CellStyle decimalStyle) {
}
