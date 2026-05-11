package tn.isetbizerte.pfe.hrbackend.modules.hr.dto;

import java.time.LocalDateTime;

public class HrManualEmailLogResponse {

    private Long id;
    private Long recipientUserId;
    private String recipientEmail;
    private Long sentByUserId;
    private String sentByUsername;
    private String sentByDisplayName;
    private String subject;
    private String bodyPreview;
    private String referenceType;
    private Long referenceId;
    private String status;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime sentAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getSentByDisplayName() {
        return sentByDisplayName;
    }

    public void setSentByDisplayName(String sentByDisplayName) {
        this.sentByDisplayName = sentByDisplayName;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBodyPreview() {
        return bodyPreview;
    }

    public void setBodyPreview(String bodyPreview) {
        this.bodyPreview = bodyPreview;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public void setReferenceType(String referenceType) {
        this.referenceType = referenceType;
    }

    public Long getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(Long referenceId) {
        this.referenceId = referenceId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }
}
