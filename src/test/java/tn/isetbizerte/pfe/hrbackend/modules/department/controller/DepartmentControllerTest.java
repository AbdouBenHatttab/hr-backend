package tn.isetbizerte.pfe.hrbackend.modules.department.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tn.isetbizerte.pfe.hrbackend.modules.department.dto.DepartmentResponse;
import tn.isetbizerte.pfe.hrbackend.modules.department.service.DepartmentService;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DepartmentControllerTest {

    private DepartmentService departmentService;
    private DepartmentController controller;

    @BeforeEach
    void setUp() {
        departmentService = mock(DepartmentService.class);
        controller = new DepartmentController(departmentService);
    }

    @Test
    void listActiveDepartmentsForSignup_returnsOnlyActiveDepartmentPayload() {
        DepartmentResponse department = new DepartmentResponse();
        department.setId(3L);
        department.setName("Engineering");
        department.setActive(true);

        when(departmentService.listActiveDepartmentsForSignup()).thenReturn(List.of(department));

        Map<String, Object> response = controller.listActiveDepartmentsForSignup();

        assertThat(response).containsEntry("success", true);
        assertThat(response.get("departments")).asList().hasSize(1);
        verify(departmentService).listActiveDepartmentsForSignup();
    }
}
