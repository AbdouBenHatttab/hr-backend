package tn.isetbizerte.pfe.hrbackend.modules.hr.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.util.List;

public class HrReportExportRequest {

    public enum SourceType {
        LEAVE,
        DOCUMENT,
        LOAN,
        AUTHORIZATION
    }

    public enum DateBasis {
        SUBMITTED,
        DECISION,
        CLOSED
    }

    public enum StatusGroup {
        PENDING,
        APPROVED,
        REJECTED,
        CANCELLED
    }

    private List<SourceType> sourceTypes;
    private DateBasis dateBasis = DateBasis.SUBMITTED;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateFrom;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateTo;

    private String status;
    private Long departmentId;
    private Long teamId;
    private StatusGroup statusGroup;
    private List<String> includeSheets;
    private String title;

    public List<SourceType> getSourceTypes() {
        return sourceTypes;
    }

    public void setSourceTypes(List<SourceType> sourceTypes) {
        this.sourceTypes = sourceTypes;
    }

    public DateBasis getDateBasis() {
        return dateBasis;
    }

    public void setDateBasis(DateBasis dateBasis) {
        this.dateBasis = dateBasis;
    }

    public LocalDate getDateFrom() {
        return dateFrom;
    }

    public void setDateFrom(LocalDate dateFrom) {
        this.dateFrom = dateFrom;
    }

    public LocalDate getDateTo() {
        return dateTo;
    }

    public void setDateTo(LocalDate dateTo) {
        this.dateTo = dateTo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Long departmentId) {
        this.departmentId = departmentId;
    }

    public Long getTeamId() {
        return teamId;
    }

    public void setTeamId(Long teamId) {
        this.teamId = teamId;
    }

    public StatusGroup getStatusGroup() {
        return statusGroup;
    }

    public void setStatusGroup(StatusGroup statusGroup) {
        this.statusGroup = statusGroup;
    }

    public List<String> getIncludeSheets() {
        return includeSheets;
    }

    public void setIncludeSheets(List<String> includeSheets) {
        this.includeSheets = includeSheets;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
