package tn.isetbizerte.pfe.hrbackend.modules.team.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CreateTeamRequest {

    @NotBlank(message = "Team name is required")
    private String name;

    private String description;

    @NotNull(message = "Team leader user ID is required")
    private Long teamLeaderId;

    public CreateTeamRequest() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Long getTeamLeaderId() { return teamLeaderId; }
    public void setTeamLeaderId(Long teamLeaderId) { this.teamLeaderId = teamLeaderId; }
}
