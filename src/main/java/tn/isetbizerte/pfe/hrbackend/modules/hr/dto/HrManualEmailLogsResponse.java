package tn.isetbizerte.pfe.hrbackend.modules.hr.dto;

import java.util.List;

public class HrManualEmailLogsResponse {

    private String message;
    private String requestedBy;
    private long totalCount;
    private int totalPages;
    private int page;
    private int size;
    private List<HrManualEmailLogResponse> logs;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(String requestedBy) {
        this.requestedBy = requestedBy;
    }

    public long getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(long totalCount) {
        this.totalCount = totalCount;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public List<HrManualEmailLogResponse> getLogs() {
        return logs;
    }

    public void setLogs(List<HrManualEmailLogResponse> logs) {
        this.logs = logs;
    }
}
