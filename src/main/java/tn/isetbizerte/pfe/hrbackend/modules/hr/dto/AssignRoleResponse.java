package tn.isetbizerte.pfe.hrbackend.modules.hr.dto;

import java.time.LocalDateTime;

/**
 * DTO for role assignment response.
 */
public class AssignRoleResponse {

    private boolean success;
    private String message;
    private Long userId;
    private String username;
    private String oldRole;
    private String newRole;
    private String assignedBy;
    private LocalDateTime timestamp;
    private boolean eventPublished;
    private String note;

    public AssignRoleResponse() {
    }

    public static AssignRoleResponseBuilder builder() {
        return new AssignRoleResponseBuilder();
    }

    public static class AssignRoleResponseBuilder {
        private final AssignRoleResponse response = new AssignRoleResponse();

        public AssignRoleResponseBuilder success(boolean success) {
            response.success = success;
            return this;
        }

        public AssignRoleResponseBuilder message(String message) {
            response.message = message;
            return this;
        }

        public AssignRoleResponseBuilder userId(Long userId) {
            response.userId = userId;
            return this;
        }

        public AssignRoleResponseBuilder username(String username) {
            response.username = username;
            return this;
        }

        public AssignRoleResponseBuilder oldRole(String oldRole) {
            response.oldRole = oldRole;
            return this;
        }

        public AssignRoleResponseBuilder newRole(String newRole) {
            response.newRole = newRole;
            return this;
        }

        public AssignRoleResponseBuilder assignedBy(String assignedBy) {
            response.assignedBy = assignedBy;
            return this;
        }

        public AssignRoleResponseBuilder timestamp(LocalDateTime timestamp) {
            response.timestamp = timestamp;
            return this;
        }

        public AssignRoleResponseBuilder eventPublished(boolean eventPublished) {
            response.eventPublished = eventPublished;
            return this;
        }

        public AssignRoleResponseBuilder note(String note) {
            response.note = note;
            return this;
        }

        public AssignRoleResponse build() {
            return response;
        }
    }

    // Getters and Setters
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getOldRole() {
        return oldRole;
    }

    public void setOldRole(String oldRole) {
        this.oldRole = oldRole;
    }

    public String getNewRole() {
        return newRole;
    }

    public void setNewRole(String newRole) {
        this.newRole = newRole;
    }

    public String getAssignedBy() {
        return assignedBy;
    }

    public void setAssignedBy(String assignedBy) {
        this.assignedBy = assignedBy;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isEventPublished() {
        return eventPublished;
    }

    public void setEventPublished(boolean eventPublished) {
        this.eventPublished = eventPublished;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}

