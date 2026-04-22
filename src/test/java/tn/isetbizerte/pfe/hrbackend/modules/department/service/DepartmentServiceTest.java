package tn.isetbizerte.pfe.hrbackend.modules.department.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tn.isetbizerte.pfe.hrbackend.common.exception.BadRequestException;
import tn.isetbizerte.pfe.hrbackend.modules.department.dto.CreateDepartmentRequest;
import tn.isetbizerte.pfe.hrbackend.modules.department.dto.DepartmentResponse;
import tn.isetbizerte.pfe.hrbackend.modules.department.dto.UpdateDepartmentRequest;
import tn.isetbizerte.pfe.hrbackend.modules.department.entity.Department;
import tn.isetbizerte.pfe.hrbackend.modules.department.repository.DepartmentRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DepartmentServiceTest {

    private DepartmentRepository departmentRepository;
    private DepartmentService departmentService;

    @BeforeEach
    void setUp() {
        departmentRepository = mock(DepartmentRepository.class);
        departmentService = new DepartmentService(departmentRepository);
    }

    @Test
    void createDepartment_rejectsDuplicateNamesIgnoringCase() {
        CreateDepartmentRequest request = new CreateDepartmentRequest();
        request.setName("engineering");

        Department existing = new Department();
        existing.setId(7L);
        existing.setName("Engineering");

        when(departmentRepository.findByNameIgnoreCase("engineering")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> departmentService.createDepartment(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Department 'engineering' already exists.");

        verify(departmentRepository, never()).save(any());
    }

    @Test
    void createDepartment_trimsValuesAndDefaultsToActive() {
        CreateDepartmentRequest request = new CreateDepartmentRequest();
        request.setName("  Finance  ");
        request.setDescription("  Handles budgets  ");

        when(departmentRepository.findByNameIgnoreCase("Finance")).thenReturn(Optional.empty());
        when(departmentRepository.save(any(Department.class))).thenAnswer(invocation -> {
            Department department = invocation.getArgument(0);
            department.setId(9L);
            return department;
        });

        DepartmentResponse response = departmentService.createDepartment(request);

        assertThat(response.getId()).isEqualTo(9L);
        assertThat(response.getName()).isEqualTo("Finance");
        assertThat(response.getDescription()).isEqualTo("Handles budgets");
        assertThat(response.getActive()).isTrue();
    }

    @Test
    void updateDepartment_canArchiveViaActiveFlag() {
        Department existing = new Department();
        existing.setId(4L);
        existing.setName("Operations");
        existing.setDescription("Ops");
        existing.setActive(true);

        UpdateDepartmentRequest request = new UpdateDepartmentRequest();
        request.setName("Operations");
        request.setDescription("Operations and support");
        request.setActive(false);

        when(departmentRepository.findById(4L)).thenReturn(Optional.of(existing));
        when(departmentRepository.findByNameIgnoreCase("Operations")).thenReturn(Optional.of(existing));
        when(departmentRepository.save(existing)).thenReturn(existing);

        DepartmentResponse response = departmentService.updateDepartment(4L, request);

        assertThat(response.getActive()).isFalse();
        assertThat(existing.getDescription()).isEqualTo("Operations and support");
    }
}
