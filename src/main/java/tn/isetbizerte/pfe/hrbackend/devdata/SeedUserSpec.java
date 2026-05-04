package tn.isetbizerte.pfe.hrbackend.devdata;

import tn.isetbizerte.pfe.hrbackend.common.enums.TypeRole;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SeedUserSpec(
        String username,
        String email,
        String firstName,
        String lastName,
        String phone,
        LocalDate birthDate,
        String address,
        String maritalStatus,
        int numberOfChildren,
        TypeRole role,
        String departmentName,
        String jobTitleName,
        LocalDate hireDate,
        BigDecimal salary
) {
}
