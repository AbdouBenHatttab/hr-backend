package tn.isetbizerte.pfe.hrbackend.modules.hr.dto;

import java.time.LocalDateTime;

public class SendHrManualEmailResponse {

    private Long logId;
    private String status;
    private String message;
    private Long recipientUserId;
    private String recipientEmail;
    private Long sentByUserId;
    private String sentByUsername;
    private LocalDateTime sentAt;

    public Long getLogId() {
        return logId;
    }

    public void setLogId(Long logId) {
        this.logId = logId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Long getRecipientUserId() {
        return recipientUserId;
    }

    public void setRecipientUserId(Long recipientUserId) {
        this.recipientUserId = recipientUserId;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public void setRecipientEmail(String recipientEmail) {
        this.recipientEmail = recipientEmail;
    }

    public Long getSentByUserId() {
        return sentByUserId;
    }

    public void setSentByUserId(Long sentByUserId) {
        this.sentByUserId = sentByUserId;
    }

    public String getSentByUsername() {
        return sentByUsername;
    }

    public void setSentByUsername(String sentByUsername) {
        this.sentByUsername = sentByUsername;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }
}
