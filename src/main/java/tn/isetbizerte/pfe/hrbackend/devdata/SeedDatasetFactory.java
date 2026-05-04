package tn.isetbizerte.pfe.hrbackend.devdata;

import tn.isetbizerte.pfe.hrbackend.common.enums.TypeRole;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SeedDatasetFactory {

    public static final List<String> DEPARTMENTS = List.of(
            "Engineering",
            "QA",
            "Product",
            "HR",
            "Finance",
            "Support",
            "Operations"
    );

    public static final Map<String, BigDecimal> JOB_TITLES = Map.ofEntries(
            Map.entry("Software Developer", bd("4200.000")),
            Map.entry("QA Engineer", bd("3600.000")),
            Map.entry("Product Analyst", bd("3900.000")),
            Map.entry("HR Specialist", bd("3400.000")),
            Map.entry("Finance Officer", bd("3700.000")),
            Map.entry("Support Agent", bd("2800.000")),
            Map.entry("Operations Coordinator", bd("3200.000")),
            Map.entry("Project Coordinator", bd("4100.000")),
            Map.entry("Backend Developer", bd("4500.000")),
            Map.entry("Frontend Developer", bd("4300.000")),
            Map.entry("Team Leader", bd("5600.000")),
            Map.entry("HR Manager", bd("6500.000"))
    );

    private static final List<String> FIRST_NAMES = List.of(
            "Amira", "Youssef", "Maya", "Karim", "Nour", "Sami", "Leila", "Rami", "Ines", "Omar",
            "Sarah", "Anis", "Hana", "Malek", "Rania", "Tarek", "Lina", "Fares", "Salma", "Adam"
    );

    private static final List<String> LAST_NAMES = List.of(
            "Ben Salem", "Trabelsi", "Mansouri", "Jaziri", "Mejri", "Gharbi", "Saidi", "Khaldi", "Cherif", "Ayari",
            "Bouzid", "Hammami", "Nasri", "Ferchichi", "Zouari", "Brahmi", "Kacem", "Toumi", "Amri", "Dridi"
    );

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    public List<SeedUserSpec> users(String usernamePrefix, String emailDomain) {
        List<SeedUserSpec> users = new ArrayList<>();
        addRoleUsers(users, usernamePrefix, emailDomain, TypeRole.HR_MANAGER, "hr", 3);
        addRoleUsers(users, usernamePrefix, emailDomain, TypeRole.TEAM_LEADER, "tl", 10);
        addRoleUsers(users, usernamePrefix, emailDomain, TypeRole.EMPLOYEE, "employee", 82);
        addRoleUsers(users, usernamePrefix, emailDomain, TypeRole.NEW_USER, "new", 5);
        return users;
    }

    private void addRoleUsers(List<SeedUserSpec> users,
                              String usernamePrefix,
                              String emailDomain,
                              TypeRole role,
                              String segment,
                              int count) {
        for (int i = 1; i <= count; i++) {
            int globalIndex = users.size() + 1;
            String username = usernamePrefix + segment + String.format("%03d", i);
            String email = username + "@" + emailDomain;
            String firstName = FIRST_NAMES.get((globalIndex - 1) % FIRST_NAMES.size());
            String lastName = LAST_NAMES.get((globalIndex - 1) % LAST_NAMES.size());
            String department = departmentFor(role, globalIndex);
            String jobTitle = jobTitleFor(role, globalIndex);
            BigDecimal salary = JOB_TITLES.get(jobTitle);
            users.add(new SeedUserSpec(
                    username,
                    email,
                    firstName,
                    lastName,
                    "+216 7" + String.format("%07d", 1000000 + globalIndex),
                    LocalDate.now().minusYears(24 + (globalIndex % 22)).minusDays(globalIndex),
                    "Seed Office " + ((globalIndex % 12) + 1) + ", Tunis",
                    globalIndex % 3 == 0 ? "Married" : "Single",
                    globalIndex % 4,
                    role,
                    department,
                    jobTitle,
                    role == TypeRole.NEW_USER ? null : LocalDate.now().minusMonths(3 + globalIndex),
                    salary
            ));
        }
    }

    private String departmentFor(TypeRole role, int index) {
        if (role == TypeRole.HR_MANAGER) {
            return "HR";
        }
        return DEPARTMENTS.get((index - 1) % DEPARTMENTS.size());
    }

    private String jobTitleFor(TypeRole role, int index) {
        if (role == TypeRole.HR_MANAGER) {
            return "HR Manager";
        }
        if (role == TypeRole.TEAM_LEADER) {
            return "Team Leader";
        }
        if (role == TypeRole.NEW_USER) {
            return switch (index % 4) {
                case 0 -> "Support Agent";
                case 1 -> "Software Developer";
                case 2 -> "QA Engineer";
                default -> "Operations Coordinator";
            };
        }
        List<String> employeeTitles = List.of(
                "Software Developer",
                "QA Engineer",
                "Product Analyst",
                "Finance Officer",
                "Support Agent",
                "Operations Coordinator",
                "Project Coordinator",
                "Backend Developer",
                "Frontend Developer"
        );
        return employeeTitles.get((index - 1) % employeeTitles.size());
    }
}
