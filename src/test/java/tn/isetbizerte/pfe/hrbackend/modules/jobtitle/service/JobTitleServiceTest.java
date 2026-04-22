package tn.isetbizerte.pfe.hrbackend.modules.jobtitle.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tn.isetbizerte.pfe.hrbackend.modules.jobtitle.dto.CreateJobTitleRequest;
import tn.isetbizerte.pfe.hrbackend.modules.jobtitle.dto.JobTitleResponse;
import tn.isetbizerte.pfe.hrbackend.modules.jobtitle.dto.UpdateJobTitleRequest;
import tn.isetbizerte.pfe.hrbackend.modules.jobtitle.entity.JobTitle;
import tn.isetbizerte.pfe.hrbackend.modules.jobtitle.repository.JobTitleRepository;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JobTitleServiceTest {

    private JobTitleRepository jobTitleRepository;
    private JobTitleService service;

    @BeforeEach
    void setUp() {
        jobTitleRepository = mock(JobTitleRepository.class);
        service = new JobTitleService(jobTitleRepository);
    }

    @Test
    void createJobTitle_trimsValuesAndDefaultsToActive() {
        CreateJobTitleRequest request = new CreateJobTitleRequest();
        request.setName("  Accountant  ");
        request.setDescription("  Handles books  ");

        when(jobTitleRepository.findByNameIgnoreCase("Accountant")).thenReturn(Optional.empty());
        when(jobTitleRepository.save(any(JobTitle.class))).thenAnswer(invocation -> {
            JobTitle jobTitle = invocation.getArgument(0);
            jobTitle.setId(9L);
            return jobTitle;
        });

        JobTitleResponse response = service.createJobTitle(request);

        assertThat(response.getId()).isEqualTo(9L);
        assertThat(response.getName()).isEqualTo("Accountant");
        assertThat(response.getDescription()).isEqualTo("Handles books");
        assertThat(response.getActive()).isTrue();
    }

    @Test
    void updateJobTitle_keepsJobTitleDescriptiveOnly() {
        JobTitle existing = new JobTitle();
        existing.setId(4L);
        existing.setName("Developer");
        existing.setActive(true);

        UpdateJobTitleRequest request = new UpdateJobTitleRequest();
        request.setName("Developer");
        request.setDescription("Builds software");

        when(jobTitleRepository.findById(4L)).thenReturn(Optional.of(existing));
        when(jobTitleRepository.findByNameIgnoreCase("Developer")).thenReturn(Optional.of(existing));
        when(jobTitleRepository.save(existing)).thenReturn(existing);

        JobTitleResponse response = service.updateJobTitle(4L, request);

        assertThat(response.getName()).isEqualTo("Developer");
        assertThat(response.getDescription()).isEqualTo("Builds software");
        verify(jobTitleRepository).save(existing);
    }

    @Test
    void archiveJobTitle_deactivatesTitle() {
        JobTitle existing = new JobTitle();
        existing.setId(7L);
        existing.setName("Consultant");
        existing.setActive(true);

        when(jobTitleRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(jobTitleRepository.save(existing)).thenReturn(existing);

        JobTitleResponse response = service.archiveJobTitle(7L);

        assertThat(response.getActive()).isFalse();
        assertThat(existing.getActive()).isFalse();
    }
}
