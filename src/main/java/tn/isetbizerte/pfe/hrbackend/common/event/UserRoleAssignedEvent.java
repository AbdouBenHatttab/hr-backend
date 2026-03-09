package tn.isetbizerte.pfe.hrbackend.common.event;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Event published when HR assigns a role to a user.
 * This event is sent to Kafka and consumed by the notification service.
 */
public class UserRoleAssignedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String oldRole;
    private String newRole;
    private String assignedBy;
    private LocalDateTime timestamp;

    public UserRoleAssignedEvent() {
    }

    public UserRoleAssignedEvent(Long userId, String username, String email,
                                  String firstName, String lastName,
                                  String oldRole, String newRole,
                                  String assignedBy, LocalDateTime timestamp) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.oldRole = oldRole;
        this.newRole = newRole;
        this.assignedBy = assignedBy;
        this.timestamp = timestamp;
    }

    // Getters and Setters
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
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

    @Override
    public String toString() {
        return "UserRoleAssignedEvent{" +
                "userId=" + userId +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", oldRole='" + oldRole + '\'' +
                ", newRole='" + newRole + '\'' +
                ", assignedBy='" + assignedBy + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}

