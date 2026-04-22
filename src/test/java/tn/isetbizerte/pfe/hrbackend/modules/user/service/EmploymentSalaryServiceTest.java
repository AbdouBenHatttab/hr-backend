package tn.isetbizerte.pfe.hrbackend.modules.user.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tn.isetbizerte.pfe.hrbackend.common.enums.TypeRole;

import static org.assertj.core.api.Assertions.assertThat;

class EmploymentSalaryServiceTest {

    private EmploymentSalaryService service;

    @BeforeEach
    void setUp() {
        service = new EmploymentSalaryService();
    }

    @Test
    void resolvesEmployeeSalaryFromRoleOnly() {
        assertThat(service.resolveEffectiveSalary(TypeRole.EMPLOYEE)).isEqualByComparingTo("2500");
    }

    @Test
    void resolvesFixedRoleSalaries() {
        assertThat(service.resolveEffectiveSalary(TypeRole.TEAM_LEADER)).isEqualByComparingTo("4000");
        assertThat(service.resolveEffectiveSalary(TypeRole.HR_MANAGER)).isEqualByComparingTo("3500");
        assertThat(service.resolveEffectiveSalary(TypeRole.NEW_USER)).isEqualByComparingTo("0");
    }
}
