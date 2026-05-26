package tn.isetbizerte.pfe.hrbackend.modules.task.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.groups.ConvertGroup;
import jakarta.validation.groups.Default;

public class TaskAssignmentPreviewRequest {

    @NotNull(message = "Task payload is required")
    @Valid
    @ConvertGroup(from = Default.class, to = PreviewTaskValidation.class)
    private CreateTaskRequest task;

    public CreateTaskRequest getTask() {
        return task;
    }

    public void setTask(CreateTaskRequest task) {
        this.task = task;
    }
}
