package tn.isetbizerte.pfe.hrbackend.modules.jobtitle.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.isetbizerte.pfe.hrbackend.modules.jobtitle.dto.CreateJobTitleRequest;
import tn.isetbizerte.pfe.hrbackend.modules.jobtitle.dto.JobTitleResponse;
import tn.isetbizerte.pfe.hrbackend.modules.jobtitle.dto.UpdateJobTitleRequest;
import tn.isetbizerte.pfe.hrbackend.modules.jobtitle.service.JobTitleService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/hr/job-titles")
public class JobTitleController {

    private final JobTitleService jobTitleService;

    public JobTitleController(JobTitleService jobTitleService) {
        this.jobTitleService = jobTitleService;
    }

    @GetMapping
    public Map<String, Object> listJobTitles(@RequestParam(defaultValue = "false") boolean activeOnly) {
        List<JobTitleResponse> jobTitles = jobTitleService.listJobTitles(activeOnly);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("jobTitles", jobTitles);
        return response;
    }

    @PostMapping
    public ResponseEntity<JobTitleResponse> createJobTitle(@RequestBody CreateJobTitleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(jobTitleService.createJobTitle(request));
    }

    @PutMapping("/{jobTitleId}")
    public JobTitleResponse updateJobTitle(
            @PathVariable Long jobTitleId,
            @RequestBody UpdateJobTitleRequest request) {
        return jobTitleService.updateJobTitle(jobTitleId, request);
    }

    @PatchMapping("/{jobTitleId}/archive")
    public JobTitleResponse archiveJobTitle(@PathVariable Long jobTitleId) {
        return jobTitleService.archiveJobTitle(jobTitleId);
    }

    @PatchMapping("/{jobTitleId}/activate")
    public JobTitleResponse activateJobTitle(@PathVariable Long jobTitleId) {
        return jobTitleService.activateJobTitle(jobTitleId);
    }
}
