package tn.isetbizerte.pfe.hrbackend.devdata;

import tn.isetbizerte.pfe.hrbackend.common.enums.TypeRole;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fixed, hand-curated cast for the final ArabSoft PFE demo.
 *
 * <p>Exactly 16 active users: 2 HR Managers, 4 Team Leaders, 10 Employees. Usernames are
 * professional (firstname.lastname) and emails use the dedicated local demo domain
 * {@code @arabsoft.tn} so that {@link FullDemoSeederService} can safely identify and delete
 * demo Keycloak users without ever touching admin/service/client accounts.</p>
 *
 * <p>This factory only produces data; all persistence + Keycloak work lives in the service.</p>
 */
public class FullDemoDatasetFactory {

    public static final String EMAIL_DOMAIN = "arabsoft.tn";

    public static final List<String> DEPARTMENTS = List.of(
            "HR",
            "Engineering",
            "Support",
            "Finance",
            "Operations"
    );

    public static final Map<String, BigDecimal> JOB_TITLES = Map.ofEntries(
            Map.entry("HR Manager", bd("6500.000")),
            Map.entry("Team Leader", bd("5600.000")),
            Map.entry("Backend Developer", bd("4500.000")),
            Map.entry("Frontend Developer", bd("4300.000")),
            Map.entry("QA Engineer", bd("3600.000")),
            Map.entry("Support Agent", bd("2800.000")),
            Map.entry("Finance Officer", bd("3700.000")),
            Map.entry("Operations Coordinator", bd("3200.000"))
    );

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    /** Returns the exact 16-user cast in a stable order (HR, then TL, then employees). */
    public List<SeedUserSpec> users() {
        List<SeedUserSpec> users = new ArrayList<>();

        // 2 HR Managers
        users.add(spec("nadia.mansouri", "Nadia", "Mansouri", TypeRole.HR_MANAGER, "HR", "HR Manager"));
        users.add(spec("sofien.kefi", "Sofien", "Kefi", TypeRole.HR_MANAGER, "HR", "HR Manager"));

        // 4 Team Leaders (one per team/department)
        users.add(spec("amine.trabelsi", "Amine", "Trabelsi", TypeRole.TEAM_LEADER, "Engineering", "Team Leader"));
        users.add(spec("leila.gharbi", "Leila", "Gharbi", TypeRole.TEAM_LEADER, "Support", "Team Leader"));
        users.add(spec("hatem.zouari", "Hatem", "Zouari", TypeRole.TEAM_LEADER, "Finance", "Team Leader"));
        users.add(spec("rania.khaldi", "Rania", "Khaldi", TypeRole.TEAM_LEADER, "Operations", "Team Leader"));

        // 10 Employees
        users.add(spec("ines.cherif", "Ines", "Cherif", TypeRole.EMPLOYEE, "Engineering", "Backend Developer"));
        users.add(spec("youssef.jaziri", "Youssef", "Jaziri", TypeRole.EMPLOYEE, "Engineering", "Frontend Developer"));
        users.add(spec("maya.saidi", "Maya", "Saidi", TypeRole.EMPLOYEE, "Engineering", "QA Engineer"));
        users.add(spec("sami.ayari", "Sami", "Ayari", TypeRole.EMPLOYEE, "Support", "Support Agent"));
        users.add(spec("omar.bouzid", "Omar", "Bouzid", TypeRole.EMPLOYEE, "Support", "Support Agent"));
        users.add(spec("hana.nasri", "Hana", "Nasri", TypeRole.EMPLOYEE, "Support", "Support Agent"));
        users.add(spec("salma.ferchichi", "Salma", "Ferchichi", TypeRole.EMPLOYEE, "Finance", "Finance Officer"));
        users.add(spec("fares.amri", "Fares", "Amri", TypeRole.EMPLOYEE, "Finance", "Finance Officer"));
        users.add(spec("lina.brahmi", "Lina", "Brahmi", TypeRole.EMPLOYEE, "Operations", "Operations Coordinator"));
        users.add(spec("tarek.kacem", "Tarek", "Kacem", TypeRole.EMPLOYEE, "Operations", "Operations Coordinator"));

        return users;
    }

    private SeedUserSpec spec(String username, String firstName, String lastName,
                              TypeRole role, String department, String jobTitle) {
        int i = Math.abs(username.hashCode()) % 1000;
        String email = username + "@" + EMAIL_DOMAIN;
        BigDecimal salary = JOB_TITLES.get(jobTitle);
        return new SeedUserSpec(
                username,
                email,
                firstName,
                lastName,
                "+216 71" + String.format("%06d", 100000 + i),
                LocalDate.of(1988 + (i % 10), 1 + (i % 12), 1 + (i % 27)),
                "Rue de la Republique " + ((i % 80) + 1) + ", Tunis",
                i % 2 == 0 ? "Single" : "Married",
                i % 3,
                role,
                department,
                jobTitle,
                LocalDate.now().minusYears(2).minusMonths(i % 18),
                salary
        );
    }
}
