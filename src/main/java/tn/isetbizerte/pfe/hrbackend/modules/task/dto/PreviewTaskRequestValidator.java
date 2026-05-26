package tn.isetbizerte.pfe.hrbackend.modules.task.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.List;

public class PreviewTaskRequestValidator implements ConstraintValidator<ValidPreviewTaskRequest, CreateTaskRequest> {

    @Override
    public boolean isValid(CreateTaskRequest request, ConstraintValidatorContext context) {
        if (request == null) {
            return true;
        }

        boolean valid = true;
        context.disableDefaultConstraintViolation();

        if (request.getStartDate() == null) {
            valid = false;
            addViolation(context, "startDate", "Start date is required");
        }

        if (request.getDueDate() == null) {
            valid = false;
            addViolation(context, "dueDate", "Due date is required");
        }

        if (request.getStartDate() != null && request.getDueDate() != null && request.getDueDate().isBefore(request.getStartDate())) {
            valid = false;
            addViolation(context, "dueDate", "Due date cannot be before start date.");
        }

        if (request.getAssignmentMode() == null) {
            valid = false;
            addViolation(context, "assignmentMode", "Assignment mode is required");
            return false;
        }

        switch (request.getAssignmentMode()) {
            case ONE -> {
                if (request.getAssigneeId() == null) {
                    valid = false;
                    addViolation(context, "assigneeId", "Assignee is required");
                }
            }
            case SELECTED -> {
                List<Long> assignees = request.getAssigneeIds();
                if (assignees == null || assignees.isEmpty()) {
                    valid = false;
                    addViolation(context, "assigneeIds", "Select at least one assignee.");
                }
            }
            case ALL -> {
                if (request.getAssigneeId() != null) {
                    valid = false;
                    addViolation(context, "assigneeId", "All mode does not accept a single assignee.");
                }
                List<Long> assignees = request.getAssigneeIds();
                if (assignees != null && !assignees.isEmpty()) {
                    valid = false;
                    addViolation(context, "assigneeIds", "All mode does not accept manual assignee selection.");
                }
            }
        }

        return valid;
    }

    private void addViolation(ConstraintValidatorContext context, String field, String message) {
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(field)
                .addConstraintViolation();
    }
}
