package tn.isetbizerte.pfe.hrbackend.modules.jobtitle.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.isetbizerte.pfe.hrbackend.common.exception.BadRequestException;
import tn.isetbizerte.pfe.hrbackend.common.exception.ResourceNotFoundException;
import tn.isetbizerte.pfe.hrbackend.modules.jobtitle.dto.CreateJobTitleRequest;
import tn.isetbizerte.pfe.hrbackend.modules.jobtitle.dto.JobTitleResponse;
import tn.isetbizerte.pfe.hrbackend.modules.jobtitle.dto.UpdateJobTitleRequest;
import tn.isetbizerte.pfe.hrbackend.modules.jobtitle.entity.JobTitle;
import tn.isetbizerte.pfe.hrbackend.modules.jobtitle.repository.JobTitleRepository;

import java.util.List;

@Service
public class JobTitleService {

    private final JobTitleRepository jobTitleRepository;

    public JobTitleService(JobTitleRepository jobTitleRepository) {
        this.jobTitleRepository = jobTitleRepository;
    }

    public List<JobTitleResponse> listJobTitles(boolean activeOnly) {
        List<JobTitle> titles = activeOnly
                ? jobTitleRepository.findByActiveTrueOrderByNameAsc()
                : jobTitleRepository.findAllByOrderByNameAsc();
        return titles.stream().map(JobTitleResponse::from).toList();
    }

    @Transactional
    public JobTitleResponse createJobTitle(CreateJobTitleRequest request) {
        String normalizedName = normalizeName(request.getName());
        ensureNameAvailable(normalizedName, null);

        JobTitle jobTitle = new JobTitle();
        jobTitle.setName(normalizedName);
        jobTitle.setDescription(normalizeDescription(request.getDescription()));
        jobTitle.setActive(true);

        return JobTitleResponse.from(jobTitleRepository.save(jobTitle));
    }

    @Transactional
    public JobTitleResponse updateJobTitle(Long jobTitleId, UpdateJobTitleRequest request) {
        JobTitle jobTitle = getJobTitleEntity(jobTitleId);
        String normalizedName = normalizeName(request.getName());
        ensureNameAvailable(normalizedName, jobTitleId);

        jobTitle.setName(normalizedName);
        jobTitle.setDescription(normalizeDescription(request.getDescription()));
        if (request.getActive() != null) {
            jobTitle.setActive(request.getActive());
        }

        return JobTitleResponse.from(jobTitleRepository.save(jobTitle));
    }

    @Transactional
    public JobTitleResponse archiveJobTitle(Long jobTitleId) {
        JobTitle jobTitle = getJobTitleEntity(jobTitleId);
        jobTitle.setActive(false);
        return JobTitleResponse.from(jobTitleRepository.save(jobTitle));
    }

    @Transactional
    public JobTitleResponse activateJobTitle(Long jobTitleId) {
        JobTitle jobTitle = getJobTitleEntity(jobTitleId);
        jobTitle.setActive(true);
        return JobTitleResponse.from(jobTitleRepository.save(jobTitle));
    }

    public JobTitle requireJobTitleForEmployment(Long jobTitleId) {
        JobTitle jobTitle = getJobTitleEntity(jobTitleId);
        if (!Boolean.TRUE.equals(jobTitle.getActive())) {
            throw new BadRequestException("Job title '" + jobTitle.getName() + "' is archived and cannot be assigned.");
        }
        return jobTitle;
    }

    public JobTitle getJobTitleEntity(Long jobTitleId) {
        return jobTitleRepository.findById(jobTitleId)
                .orElseThrow(() -> new ResourceNotFoundException("Job title not found with ID: " + jobTitleId));
    }

    private void ensureNameAvailable(String name, Long currentJobTitleId) {
        jobTitleRepository.findByNameIgnoreCase(name).ifPresent(existing -> {
            if (currentJobTitleId == null || !existing.getId().equals(currentJobTitleId)) {
                throw new BadRequestException("Job title '" + name + "' already exists.");
            }
        });
    }

    private String normalizeName(String name) {
        String normalized = name != null ? name.trim() : "";
        if (normalized.isBlank()) {
            throw new BadRequestException("Job title name is required.");
        }
        return normalized;
    }

    private String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String normalized = description.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
