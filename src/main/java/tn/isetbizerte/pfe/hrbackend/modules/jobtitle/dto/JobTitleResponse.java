package tn.isetbizerte.pfe.hrbackend.modules.jobtitle.dto;

import tn.isetbizerte.pfe.hrbackend.modules.jobtitle.entity.JobTitle;

import java.time.LocalDateTime;

public class JobTitleResponse {

    private Long id;
    private String name;
    private String description;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static JobTitleResponse from(JobTitle jobTitle) {
        JobTitleResponse response = new JobTitleResponse();
        response.setId(jobTitle.getId());
        response.setName(jobTitle.getName());
        response.setDescription(jobTitle.getDescription());
        response.setActive(jobTitle.getActive());
        response.setCreatedAt(jobTitle.getCreatedAt());
        response.setUpdatedAt(jobTitle.getUpdatedAt());
        return response;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
