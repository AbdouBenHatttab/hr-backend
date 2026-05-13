package tn.isetbizerte.pfe.hrbackend.modules.team.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class UpdateTeamRequest {

    @NotBlank(message = "Team name is required")
    private String name;

    @NotNull(message = "Department is required")
    private Long departmentId;

    private String description;

    public UpdateTeamRequest() {}

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Long departmentId) {
        this.departmentId = departmentId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
