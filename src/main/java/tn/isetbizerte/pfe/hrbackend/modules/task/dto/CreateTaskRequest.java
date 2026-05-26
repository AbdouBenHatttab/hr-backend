package tn.isetbizerte.pfe.hrbackend.modules.task.dto;

import jakarta.validation.constraints.NotBlank;
import tn.isetbizerte.pfe.hrbackend.common.enums.TaskAssignmentMode;
import tn.isetbizerte.pfe.hrbackend.common.enums.TaskPriority;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@ValidPreviewTaskRequest(groups = PreviewTaskValidation.class)
public class CreateTaskRequest {

    @NotBlank(message = "Task title is required", groups = CreateTaskValidation.class)
    private String title;

    private String description;

    private TaskPriority priority = TaskPriority.MEDIUM;

    private LocalDate startDate;
    private LocalDate dueDate;

    private Long assigneeId;
    private List<Long> assigneeIds = new ArrayList<>();
    private TaskAssignmentMode assignmentMode = TaskAssignmentMode.ONE;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public TaskPriority getPriority() { return priority; }
    public void setPriority(TaskPriority priority) { this.priority = priority; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public Long getAssigneeId() { return assigneeId; }
    public void setAssigneeId(Long assigneeId) { this.assigneeId = assigneeId; }

    public List<Long> getAssigneeIds() { return assigneeIds; }
    public void setAssigneeIds(List<Long> assigneeIds) {
        this.assigneeIds = assigneeIds != null ? assigneeIds : new ArrayList<>();
    }

    public TaskAssignmentMode getAssignmentMode() { return assignmentMode; }
    public void setAssignmentMode(TaskAssignmentMode assignmentMode) {
        this.assignmentMode = assignmentMode != null ? assignmentMode : TaskAssignmentMode.ONE;
    }
}
