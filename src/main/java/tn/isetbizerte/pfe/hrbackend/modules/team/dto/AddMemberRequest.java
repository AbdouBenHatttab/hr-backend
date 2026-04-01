package tn.isetbizerte.pfe.hrbackend.modules.team.dto;

import jakarta.validation.constraints.NotNull;

public class AddMemberRequest {

    @NotNull(message = "Employee user ID is required")
    private Long employeeId;

    public AddMemberRequest() {}

    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
}
