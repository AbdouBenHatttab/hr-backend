package tn.isetbizerte.pfe.hrbackend.modules.task.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public class TaskAssignmentPreviewRequest {

    @NotNull(message = "Task payload is required")
    @Valid
    private CreateTaskRequest task;

    public CreateTaskRequest getTask() {
        return task;
    }

    public void setTask(CreateTaskRequest task) {
        this.task = task;
    }
}
