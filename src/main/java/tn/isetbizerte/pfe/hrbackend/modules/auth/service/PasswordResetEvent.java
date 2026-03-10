package tn.isetbizerte.pfe.hrbackend.modules.auth.service;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Event published when a user requests a password reset.
 * This event is sent to Kafka and consumed by the email notification service.
 */
public class PasswordResetEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private String email;
    private String firstName;
    private String lastName;
    private String resetToken;
    private LocalDateTime timestamp;
    private LocalDateTime expiryTime;

    public PasswordResetEvent() {
    }

    public PasswordResetEvent(String email, String firstName, String lastName,
                               String resetToken, LocalDateTime timestamp, LocalDateTime expiryTime) {
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.resetToken = resetToken;
        this.timestamp = timestamp;
        this.expiryTime = expiryTime;
    }

    // Getters and Setters
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

    public String getResetToken() {
        return resetToken;
    }

    public void setResetToken(String resetToken) {
        this.resetToken = resetToken;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public LocalDateTime getExpiryTime() {
        return expiryTime;
    }

    public void setExpiryTime(LocalDateTime expiryTime) {
        this.expiryTime = expiryTime;
    }

    @Override
    public String toString() {
        return "PasswordResetEvent{" +
                "email='" + email + '\'' +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", resetToken='" + resetToken + '\'' +
                ", timestamp=" + timestamp +
                ", expiryTime=" + expiryTime +
                '}';
    }
}

