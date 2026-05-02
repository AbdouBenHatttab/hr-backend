package tn.isetbizerte.pfe.hrbackend.modules.hr.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;

public class CompleteUserSetupRequest {

    private String role;
    private Long departmentId;
    private Long jobTitleId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate hireDate;

    private Long teamId;
    private Long ledTeamId;

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Long departmentId) {
        this.departmentId = departmentId;
    }

    public Long getJobTitleId() {
        return jobTitleId;
    }

    public void setJobTitleId(Long jobTitleId) {
        this.jobTitleId = jobTitleId;
    }

    public LocalDate getHireDate() {
        return hireDate;
    }

    public void setHireDate(LocalDate hireDate) {
        this.hireDate = hireDate;
    }

    public Long getTeamId() {
        return teamId;
    }

    public void setTeamId(Long teamId) {
        this.teamId = teamId;
    }

    public Long getLedTeamId() {
        return ledTeamId;
    }

    public void setLedTeamId(Long ledTeamId) {
        this.ledTeamId = ledTeamId;
    }
}
