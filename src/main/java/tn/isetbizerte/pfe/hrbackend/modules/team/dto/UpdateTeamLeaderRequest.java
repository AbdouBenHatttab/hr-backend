package tn.isetbizerte.pfe.hrbackend.modules.team.dto;

import jakarta.validation.constraints.NotNull;

public class UpdateTeamLeaderRequest {

    @NotNull(message = "Team leader user ID is required")
    private Long teamLeaderId;

    public UpdateTeamLeaderRequest() {}

    public Long getTeamLeaderId() { return teamLeaderId; }
    public void setTeamLeaderId(Long teamLeaderId) { this.teamLeaderId = teamLeaderId; }
}
