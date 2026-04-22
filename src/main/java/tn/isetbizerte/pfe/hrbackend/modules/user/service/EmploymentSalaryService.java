package tn.isetbizerte.pfe.hrbackend.modules.user.service;

import org.springframework.stereotype.Service;
import tn.isetbizerte.pfe.hrbackend.common.enums.TypeRole;

import java.math.BigDecimal;

@Service
public class EmploymentSalaryService {

    public BigDecimal resolveEffectiveSalary(TypeRole role) {
        if (role == null) {
            return BigDecimal.ZERO;
        }
        return switch (role) {
            case EMPLOYEE -> new BigDecimal("2500");
            case TEAM_LEADER -> new BigDecimal("4000");
            case HR_MANAGER -> new BigDecimal("3500");
            case NEW_USER -> BigDecimal.ZERO;
        };
    }
}
