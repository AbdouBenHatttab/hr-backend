package tn.isetbizerte.pfe.hrbackend.modules.hr.service;

/**
 * Describes the expected data type of each detail column for export sanitization.
 */
enum HrReportDetailColumnKind {
    TEXT,
    DATE,
    DATETIME,
    TIME,
    NUMBER,
    OPTIONAL_NUMBER
}
