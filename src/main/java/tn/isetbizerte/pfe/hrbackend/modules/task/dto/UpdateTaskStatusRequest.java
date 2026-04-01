package tn.isetbizerte.pfe.hrbackend.modules.task.dto;

import jakarta.validation.constraints.NotNull;
import tn.isetbizerte.pfe.hrbackend.common.enums.TaskStatus;

public class UpdateTaskStatusRequest {

    @NotNull(message = "Status is required")
    private TaskStatus status;

    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
}
