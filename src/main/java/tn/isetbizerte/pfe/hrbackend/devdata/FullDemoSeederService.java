package tn.isetbizerte.pfe.hrbackend.devdata;

import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tn.isetbizerte.pfe.hrbackend.common.enums.ApprovalDecision;
import tn.isetbizerte.pfe.hrbackend.common.enums.AuthorizationType;
import tn.isetbizerte.pfe.hrbackend.common.enums.DocumentFulfillmentMode;
import tn.isetbizerte.pfe.hrbackend.common.enums.DocumentType;
import tn.isetbizerte.pfe.hrbackend.common.enums.LeaveStatus;
import tn.isetbizerte.pfe.hrbackend.common.enums.LeaveType;
import tn.isetbizerte.pfe.hrbackend.common.enums.LoanType;
import tn.isetbizerte.pfe.hrbackend.common.enums.RequestStatus;
import tn.isetbizerte.pfe.hrbackend.common.enums.TaskPriority;
import tn.isetbizerte.pfe.hrbackend.common.enums.TaskStatus;
import tn.isetbizerte.pfe.hrbackend.common.enums.TypeRole;
import tn.isetbizerte.pfe.hrbackend.infrastructure.storage.DocumentAttachmentStorageService;
import tn.isetbizerte.pfe.hrbackend.infrastructure.storage.StoredAttachment;
import tn.isetbizerte.pfe.hrbackend.modules.department.entity.Department;
import tn.isetbizerte.pfe.hrbackend.modules.department.repository.DepartmentRepository;
import tn.isetbizerte.pfe.hrbackend.modules.employee.entity.EmployeeLeaveBalance;
import tn.isetbizerte.pfe.hrbackend.modules.employee.entity.LeaveRequest;
import tn.isetbizerte.pfe.hrbackend.modules.employee.repository.EmployeeLeaveBalanceRepository;
import tn.isetbizerte.pfe.hrbackend.modules.employee.repository.LeaveRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.history.entity.RequestHistory;
import tn.isetbizerte.pfe.hrbackend.modules.history.repository.RequestHistoryRepository;
import tn.isetbizerte.pfe.hrbackend.modules.hr.entity.HrManualEmailLog;
import tn.isetbizerte.pfe.hrbackend.modules.hr.entity.HrManualEmailStatus;
import tn.isetbizerte.pfe.hrbackend.modules.hr.repository.HrManualEmailLogRepository;
import tn.isetbizerte.pfe.hrbackend.modules.hr.service.KeycloakAdminService;
import tn.isetbizerte.pfe.hrbackend.modules.jobtitle.entity.JobTitle;
import tn.isetbizerte.pfe.hrbackend.modules.jobtitle.repository.JobTitleRepository;
import tn.isetbizerte.pfe.hrbackend.modules.notification.entity.Notification;
import tn.isetbizerte.pfe.hrbackend.modules.notification.repository.NotificationRepository;
import tn.isetbizerte.pfe.hrbackend.modules.requests.entity.AuthorizationRequest;
import tn.isetbizerte.pfe.hrbackend.modules.requests.entity.DocumentRequest;
import tn.isetbizerte.pfe.hrbackend.modules.requests.entity.LoanRequest;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.AuthorizationRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.DocumentRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.LoanRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.task.entity.Project;
import tn.isetbizerte.pfe.hrbackend.modules.task.entity.Task;
import tn.isetbizerte.pfe.hrbackend.modules.task.repository.ProjectRepository;
import tn.isetbizerte.pfe.hrbackend.modules.task.repository.TaskRepository;
import tn.isetbizerte.pfe.hrbackend.modules.team.entity.Team;
import tn.isetbizerte.pfe.hrbackend.modules.team.repository.TeamRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.Person;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.PersonRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.UserRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Destructive, heavily-guarded local PFE demo reset + seed.
 *
 * <p>Wipes ALL local application business data and demo Keycloak users, then recreates one
 * clean, coherent ArabSoft dataset (16 active users + full workflow data). Guarded so it can
 * only ever run against a local demo database/Keycloak under the {@code demo-reset} profile
 * with an explicit confirmation token. Reference data ({@code leave_policies},
 * {@code public_holidays}, {@code flyway_schema_history}) and PDF/report/QR logic are untouched.</p>
 *
 * <p>The pre-existing {@link DevDataSeederService} (seed-scoped) is intentionally left in place;
 * this service is a separate, full-wipe variant.</p>
 */
@Service
@Profile("demo-reset")
public class FullDemoSeederService {

    private static final Logger log = LoggerFactory.getLogger(FullDemoSeederService.class);

    private static final String REQUIRED_PROFILE = "demo-reset";
    private static final String REQUIRED_CONFIRM = "RESET_LOCAL_ARABSOFT_DEMO";
    private static final String REQUIRED_DB_NAME = "hr_db";
    private static final String REQUIRED_REALM = "hr-realm";
    private static final List<String> ROLES = List.of("HR_MANAGER", "TEAM_LEADER", "EMPLOYEE", "NEW_USER");

    private final DevSeedProperties properties;
    private final Environment environment;
    private final String datasourceUrl;
    private final String keycloakUrl;
    private final String keycloakRealm;
    private final KeycloakAdminService keycloakAdminService;
    private final TransactionTemplate tx;
    private final EntityManager entityManager;
    private final UserRepository userRepository;
    private final PersonRepository personRepository;
    private final DepartmentRepository departmentRepository;
    private final JobTitleRepository jobTitleRepository;
    private final TeamRepository teamRepository;
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final EmployeeLeaveBalanceRepository leaveBalanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final DocumentRequestRepository documentRequestRepository;
    private final LoanRequestRepository loanRequestRepository;
    private final AuthorizationRequestRepository authorizationRequestRepository;
    private final RequestHistoryRepository requestHistoryRepository;
    private final NotificationRepository notificationRepository;
    private final HrManualEmailLogRepository hrManualEmailLogRepository;
    private final DocumentAttachmentStorageService attachmentStorage;
    private final FullDemoDatasetFactory datasetFactory = new FullDemoDatasetFactory();

    private final int year = LocalDate.now().getYear();

    public FullDemoSeederService(DevSeedProperties properties,
                                 Environment environment,
                                 @Value("${spring.datasource.url}") String datasourceUrl,
                                 @Value("${keycloak.auth-server-url}") String keycloakUrl,
                                 @Value("${keycloak.realm}") String keycloakRealm,
                                 KeycloakAdminService keycloakAdminService,
                                 PlatformTransactionManager transactionManager,
                                 EntityManager entityManager,
                                 UserRepository userRepository,
                                 PersonRepository personRepository,
                                 DepartmentRepository departmentRepository,
                                 JobTitleRepository jobTitleRepository,
                                 TeamRepository teamRepository,
                                 ProjectRepository projectRepository,
                                 TaskRepository taskRepository,
                                 EmployeeLeaveBalanceRepository leaveBalanceRepository,
                                 LeaveRequestRepository leaveRequestRepository,
                                 DocumentRequestRepository documentRequestRepository,
                                 LoanRequestRepository loanRequestRepository,
                                 AuthorizationRequestRepository authorizationRequestRepository,
                                 RequestHistoryRepository requestHistoryRepository,
                                 NotificationRepository notificationRepository,
                                 HrManualEmailLogRepository hrManualEmailLogRepository,
                                 DocumentAttachmentStorageService attachmentStorage) {
        this.properties = properties;
        this.environment = environment;
        this.datasourceUrl = datasourceUrl;
        this.keycloakUrl = keycloakUrl;
        this.keycloakRealm = keycloakRealm;
        this.keycloakAdminService = keycloakAdminService;
        this.tx = new TransactionTemplate(transactionManager);
        this.entityManager = entityManager;
        this.userRepository = userRepository;
        this.personRepository = personRepository;
        this.departmentRepository = departmentRepository;
        this.jobTitleRepository = jobTitleRepository;
        this.teamRepository = teamRepository;
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.documentRequestRepository = documentRequestRepository;
        this.loanRequestRepository = loanRequestRepository;
        this.authorizationRequestRepository = authorizationRequestRepository;
        this.requestHistoryRepository = requestHistoryRepository;
        this.notificationRepository = notificationRepository;
        this.hrManualEmailLogRepository = hrManualEmailLogRepository;
        this.attachmentStorage = attachmentStorage;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Orchestration
    // ──────────────────────────────────────────────────────────────────────────
    public void resetAndSeed() {
        validateSafetyGuards();

        log.warn("=== DESTRUCTIVE LOCAL DEMO RESET === Wiping ALL ArabSoft application data and demo "
                + "Keycloak users in realm '{}' / database '{}'. Reference data (leave_policies, "
                + "public_holidays, flyway_schema_history) and PDF/QR logic are preserved.",
                keycloakRealm, REQUIRED_DB_NAME);

        if (!keycloakAdminService.ensureRealmRolesExist(ROLES)) {
            throw new IllegalStateException("FullDemoSeeder aborted: Keycloak realm roles are unavailable.");
        }

        // 1) Read what we need BEFORE wiping (Keycloak ids + on-disk attachment paths).
        List<String> existingKeycloakIds = tx.execute(s -> selectStrings(
                "select u.keycloakId from User u where u.keycloakId is not null and u.keycloakId <> ''"));
        Set<String> attachmentPaths = tx.execute(s -> collectAttachmentPaths());

        // 2) Delete on-disk seeded/demo attachment files (best-effort).
        deleteUploads(attachmentPaths);

        // 3) Full PostgreSQL wipe (single transaction, FK-safe order).
        tx.executeWithoutResult(s -> wipeAllPostgreSql());

        // 4) Delete old demo Keycloak users (never blanket-delete the realm).
        List<SeedUserSpec> specs = datasetFactory.users();
        deleteOldKeycloakUsers(existingKeycloakIds, specs);

        // 5) Recreate exactly the 16 Keycloak users with role + password.
        Map<String, String> keycloakIds = createKeycloakUsers(specs);

        // 6) Seed clean PostgreSQL dataset (single transaction).
        tx.executeWithoutResult(s -> seedPostgreSql(specs, keycloakIds));

        log.warn("=== FULL DEMO RESET COMPLETED === {} users recreated (2 HR / 4 TL / 10 EMPLOYEE). "
                + "All demo users can login with the configured password.", specs.size());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Safety
    // ──────────────────────────────────────────────────────────────────────────
    private void validateSafetyGuards() {
        List<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
        require(activeProfiles.contains(REQUIRED_PROFILE), "active profile must contain '" + REQUIRED_PROFILE + "'");
        require(properties.isEnabled(), "app.dev-seed.enabled must be true");
        require(properties.isReset(), "app.dev-seed.reset must be true");
        require(REQUIRED_CONFIRM.equals(properties.getConfirm()),
                "app.dev-seed.confirm must be '" + REQUIRED_CONFIRM + "'");
        require(properties.getPassword() != null && !properties.getPassword().isBlank(),
                "app.dev-seed.password must not be blank");
        require(isLocalUrl(datasourceUrl), "spring.datasource.url must point to a local database");
        require(datasourceUrl != null && datasourceUrl.toLowerCase(Locale.ROOT).contains(REQUIRED_DB_NAME),
                "spring.datasource.url must target the local demo database '" + REQUIRED_DB_NAME + "'");
        require(isLocalUrl(keycloakUrl), "keycloak.auth-server-url must point to local Keycloak");
        require(REQUIRED_REALM.equalsIgnoreCase(keycloakRealm),
                "keycloak.realm must be the local demo realm '" + REQUIRED_REALM + "'");
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("FullDemoSeeder refused to run: " + message);
        }
    }

    private boolean isLocalUrl(String value) {
        if (value == null) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("localhost")
                || lower.contains("127.0.0.1")
                || lower.contains("host.docker.internal")
                || lower.contains("//postgres")
                || lower.contains("//hr-postgres")
                || lower.contains("//keycloak")
                || lower.contains("//hr-keycloak");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // PostgreSQL wipe
    // ──────────────────────────────────────────────────────────────────────────
    private Set<String> collectAttachmentPaths() {
        Set<String> paths = new HashSet<>();
        paths.addAll(selectStrings("select r.attachmentStoragePath from DocumentRequest r where r.attachmentStoragePath is not null"));
        paths.addAll(selectStrings("select r.attachmentStoragePath from LoanRequest r where r.attachmentStoragePath is not null"));
        paths.addAll(selectStrings("select d.storagePath from StoredEmployeeDocument d where d.storagePath is not null"));
        paths.removeIf(p -> p == null || p.isBlank());
        return paths;
    }

    private void wipeAllPostgreSql() {
        // Child / audit tables first, then break the user<->team cycle, then identities.
        bulk("delete from Notification n");
        bulk("delete from RequestHistory h");
        bulk("delete from HrManualEmailLog e");
        bulk("delete from OutboxEvent o");
        bulk("delete from ProcessedEvent p");
        bulk("delete from LoginHistory l");
        bulk("delete from PasswordResetToken t");
        bulk("delete from PasswordChangeToken t");
        bulk("delete from Task t");
        bulk("delete from Project p");
        bulk("delete from EmployeeLeaveBalance b");
        bulk("delete from LeaveRequest r");
        bulk("delete from DocumentRequest r");
        bulk("delete from LoanRequest r");
        bulk("delete from AuthorizationRequest r");
        bulk("delete from StoredEmployeeDocument d");
        bulk("update User u set u.team = null");
        bulk("update Team t set t.teamLeader = null");
        bulk("delete from Team t");
        bulk("delete from User u");
        bulk("delete from Person p");
        // Keep: leave_policies, public_holidays, flyway_schema_history. Departments/job titles
        // are re-used/upserted during seeding (not deleted) to avoid breaking reference data.
    }

    private void bulk(String jpql) {
        entityManager.createQuery(jpql).executeUpdate();
    }

    private List<String> selectStrings(String jpql) {
        return entityManager.createQuery(jpql, String.class).getResultList();
    }

    private void deleteUploads(Set<String> attachmentPaths) {
        for (String path : attachmentPaths) {
            try {
                attachmentStorage.deleteIfExists(path);
            } catch (Exception e) {
                log.warn("FullDemoSeeder could not delete attachment '{}': {}", path, e.getMessage());
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Keycloak
    // ──────────────────────────────────────────────────────────────────────────
    private void deleteOldKeycloakUsers(List<String> existingKeycloakIds, List<SeedUserSpec> specs) {
        Set<String> ids = new HashSet<>();
        if (existingKeycloakIds != null) {
            ids.addAll(existingKeycloakIds);
        }
        // Fallback: target usernames of the new cast (catch orphaned KC users with no PG row).
        for (SeedUserSpec spec : specs) {
            String id = keycloakAdminService.findKeycloakUserIdByUsername(spec.username());
            if (id != null && !id.isBlank()) {
                ids.add(id);
            }
        }
        for (String id : ids) {
            if (id == null || id.isBlank()) {
                continue;
            }
            if (!keycloakAdminService.deleteUserById(id)) {
                throw new IllegalStateException(
                        "FullDemoSeeder aborted before recreate: failed to delete old Keycloak user " + id);
            }
        }
    }

    private Map<String, String> createKeycloakUsers(List<SeedUserSpec> specs) {
        Map<String, String> ids = new LinkedHashMap<>();
        for (SeedUserSpec spec : specs) {
            String id = keycloakAdminService.createUser(
                    spec.username(),
                    spec.email(),
                    spec.firstName(),
                    spec.lastName(),
                    properties.getPassword(),
                    true,
                    true
            );
            if (id == null || id.isBlank()) {
                throw new IllegalStateException("FullDemoSeeder aborted: failed to create Keycloak user " + spec.username());
            }
            if (!keycloakAdminService.assignRoleToUser(id, spec.role().name())) {
                throw new IllegalStateException("FullDemoSeeder aborted: failed to assign role for " + spec.username());
            }
            ids.put(spec.username(), id);
        }
        return ids;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // PostgreSQL seed
    // ──────────────────────────────────────────────────────────────────────────
    private void seedPostgreSql(List<SeedUserSpec> specs, Map<String, String> keycloakIds) {
        Map<String, Department> departments = ensureDepartments();
        Map<String, JobTitle> jobTitles = ensureJobTitles();
        Map<String, User> users = createLocalUsers(specs, keycloakIds, departments, jobTitles);
        Map<String, Team> teams = createTeamsAndAssign(users);

        seedLeaveBalances(users);
        seedLeaveRequests(users);
        seedDocumentRequests(users);
        seedLoanRequests(users);
        seedAuthorizationRequests(users);
        seedProjectsAndTasks(users, teams);
        seedHrManualEmails(users);

        entityManager.flush();
    }

    private Map<String, Department> ensureDepartments() {
        Map<String, Department> map = new LinkedHashMap<>();
        for (String name : FullDemoDatasetFactory.DEPARTMENTS) {
            Department department = departmentRepository.findByNameIgnoreCase(name)
                    .orElseGet(() -> {
                        Department d = new Department();
                        d.setName(name);
                        d.setDescription("ArabSoft " + name + " department");
                        d.setActive(true);
                        return departmentRepository.save(d);
                    });
            map.put(name, department);
        }
        return map;
    }

    private Map<String, JobTitle> ensureJobTitles() {
        Map<String, JobTitle> map = new LinkedHashMap<>();
        for (Map.Entry<String, BigDecimal> entry : FullDemoDatasetFactory.JOB_TITLES.entrySet()) {
            JobTitle jobTitle = jobTitleRepository.findByNameIgnoreCase(entry.getKey())
                    .orElseGet(() -> {
                        JobTitle j = new JobTitle();
                        j.setName(entry.getKey());
                        j.setDescription("ArabSoft job title");
                        j.setActive(true);
                        j.setDefaultSalary(entry.getValue());
                        return jobTitleRepository.save(j);
                    });
            map.put(entry.getKey(), jobTitle);
        }
        return map;
    }

    private Map<String, User> createLocalUsers(List<SeedUserSpec> specs,
                                               Map<String, String> keycloakIds,
                                               Map<String, Department> departments,
                                               Map<String, JobTitle> jobTitles) {
        Map<String, User> users = new LinkedHashMap<>();
        int index = 0;
        for (SeedUserSpec spec : specs) {
            Person person = new Person();
            person.setFirstName(spec.firstName());
            person.setLastName(spec.lastName());
            person.setEmail(spec.email());
            person.setPhone(spec.phone());
            person.setBirthDate(spec.birthDate());
            person.setAddress(spec.address());
            person.setMaritalStatus(spec.maritalStatus());
            person.setNumberOfChildren(spec.numberOfChildren());
            person.setDepartmentRef(departments.get(spec.departmentName()));
            person.setJobTitleRef(jobTitles.get(spec.jobTitleName()));
            person.setHireDate(spec.hireDate());
            person.setSalary(spec.salary());
            person.setAvatarColor(avatarColor(index++));

            User user = new User(keycloakIds.get(spec.username()), spec.username());
            user.setRole(spec.role());
            user.setActive(true);
            user.setEmailVerified(true);
            user.setPerson(person);
            person.setUser(user);

            personRepository.save(person);
            users.put(spec.username(), userRepository.save(user));
        }
        return users;
    }

    private String avatarColor(int index) {
        List<String> colors = List.of("bg-sky-600", "bg-emerald-600", "bg-rose-600", "bg-amber-600", "bg-indigo-600");
        return colors.get(index % colors.size());
    }

    private Map<String, Team> createTeamsAndAssign(Map<String, User> users) {
        Map<String, Team> teams = new LinkedHashMap<>();
        teams.put("Engineering", createTeam("Engineering", users.get("amine.trabelsi")));
        teams.put("Support", createTeam("Support", users.get("leila.gharbi")));
        teams.put("Finance", createTeam("Finance", users.get("hatem.zouari")));
        teams.put("Operations", createTeam("Operations", users.get("rania.khaldi")));

        // Assign each employee to the team matching their department.
        for (User user : users.values()) {
            if (user.getRole() != TypeRole.EMPLOYEE) {
                continue;
            }
            String dept = user.getPerson().getDepartmentRef() != null
                    ? user.getPerson().getDepartmentRef().getName() : null;
            Team team = dept != null ? teams.get(dept) : null;
            if (team != null) {
                user.setTeam(team);
                userRepository.save(user);
            }
        }
        return teams;
    }

    private Team createTeam(String name, User leader) {
        Team team = new Team();
        team.setName(name);
        team.setDescription("ArabSoft " + name + " team");
        team.setMaxLeavePercentage(40);
        team.setTeamLeader(leader);
        return teamRepository.save(team);
    }

    // ── Leave balances (ANNUAL, must match seeded leave requests) ──
    private void seedLeaveBalances(Map<String, User> users) {
        // employee -> {allocated, carryForward, used, reserved}
        annualBalance(users, "ines.cherif", 30, 5, 6, 0);
        annualBalance(users, "youssef.jaziri", 30, 0, 10, 5);
        annualBalance(users, "maya.saidi", 20, 0, 0, 4);
        annualBalance(users, "sami.ayari", 20, 0, 0, 3);
        annualBalance(users, "omar.bouzid", 20, 0, 0, 0);
        annualBalance(users, "hana.nasri", 20, 0, 0, 0);
        annualBalance(users, "salma.ferchichi", 30, 0, 30, 0);
        annualBalance(users, "fares.amri", 15, 0, 0, 0);
        annualBalance(users, "lina.brahmi", 15, 0, 0, 0);
        annualBalance(users, "tarek.kacem", 15, 0, 0, 0);
    }

    private void annualBalance(Map<String, User> users, String username,
                               int allocated, int carryForward, int used, int reserved) {
        User user = users.get(username);
        EmployeeLeaveBalance balance = new EmployeeLeaveBalance(user, LeaveType.ANNUAL, year, BigDecimal.valueOf(allocated));
        balance.setCarryForwardDays(BigDecimal.valueOf(carryForward));
        balance.setUsedDays(BigDecimal.valueOf(used));
        balance.setReservedDays(BigDecimal.valueOf(reserved));
        balance.setLastAccruedMonth(LocalDate.now().getMonthValue());
        leaveBalanceRepository.save(balance);
    }

    // ── Leave requests ──
    private void seedLeaveRequests(Map<String, User> users) {
        User hr1 = users.get("nadia.mansouri");

        // L1 — pending Team Leader review (reserves 4 for Maya)
        LeaveRequest l1 = baseLeave(users.get("maya.saidi"), LeaveType.ANNUAL, future(2), 4,
                "Family logistics during school break.");
        l1.setStatus(LeaveStatus.PENDING);
        l1.setTeamLeaderDecision(ApprovalDecision.PENDING);
        l1.setHrDecision(ApprovalDecision.PENDING);
        l1 = leaveRequestRepository.save(l1);
        history("LEAVE", "CREATED", l1.getId(), users.get("maya.saidi").getKeycloakId(), l1.getReason(), null, "PENDING_TL");
        notify(users.get("maya.saidi"), "Leave request submitted, awaiting team leader review", "LEAVE_STATUS", "LEAVE", l1.getId(), "/employee/leaves?leaveId=" + l1.getId());

        // L2 — pending HR review (TL approved; reserves 3 for Sami)
        LeaveRequest l2 = baseLeave(users.get("sami.ayari"), LeaveType.ANNUAL, future(3), 3,
                "Personal appointment.");
        l2.setStatus(LeaveStatus.PENDING);
        l2.setTeamLeaderDecision(ApprovalDecision.APPROVED);
        l2.setHrDecision(ApprovalDecision.PENDING);
        l2 = leaveRequestRepository.save(l2);
        history("LEAVE", "CREATED", l2.getId(), users.get("sami.ayari").getKeycloakId(), l2.getReason(), null, "PENDING_TL");
        history("LEAVE", "TL_APPROVED", l2.getId(), users.get("leila.gharbi").getKeycloakId(), "Team leader approved.", "PENDING_TL", "PENDING_HR");
        notify(users.get("sami.ayari"), "Leave approved by team leader, awaiting HR decision", "LEAVE_STATUS", "LEAVE", l2.getId(), "/employee/leaves?leaveId=" + l2.getId());

        // L3 — fully approved with downloadable Leave Approval Certificate (uses 6 for Ines)
        LeaveRequest l3 = baseLeave(users.get("ines.cherif"), LeaveType.ANNUAL, past(6), 6,
                "Annual vacation.");
        l3.setStatus(LeaveStatus.APPROVED);
        l3.setTeamLeaderDecision(ApprovalDecision.APPROVED);
        l3.setHrDecision(ApprovalDecision.APPROVED);
        l3.setApprovedBy(hr1.getKeycloakId());
        l3.setApprovalDate(LocalDate.now().minusWeeks(6).plusDays(7));
        l3.setApprovedAt(LocalDateTime.now().minusWeeks(6).plusDays(1));
        l3.setVerificationToken(UUID.randomUUID().toString());
        l3 = leaveRequestRepository.save(l3);
        history("LEAVE", "CREATED", l3.getId(), users.get("ines.cherif").getKeycloakId(), l3.getReason(), null, "PENDING_TL");
        history("LEAVE", "TL_APPROVED", l3.getId(), users.get("amine.trabelsi").getKeycloakId(), "Team leader approved.", "PENDING_TL", "PENDING_HR");
        history("LEAVE", "HR_APPROVED", l3.getId(), hr1.getKeycloakId(), "Approved by HR.", "PENDING_HR", "APPROVED");
        notify(users.get("ines.cherif"), "Leave approved — certificate available", "LEAVE_STATUS", "LEAVE", l3.getId(), "/employee/leaves?leaveId=" + l3.getId());

        // L4 — rejected (released; no balance effect for Omar)
        LeaveRequest l4 = baseLeave(users.get("omar.bouzid"), LeaveType.ANNUAL, past(4), 2,
                "Short trip request.");
        l4.setStatus(LeaveStatus.REJECTED);
        l4.setTeamLeaderDecision(ApprovalDecision.APPROVED);
        l4.setHrDecision(ApprovalDecision.REJECTED);
        l4.setRejectedBy(hr1.getKeycloakId());
        l4.setRejectedAt(LocalDateTime.now().minusWeeks(4).plusDays(1));
        l4 = leaveRequestRepository.save(l4);
        history("LEAVE", "CREATED", l4.getId(), users.get("omar.bouzid").getKeycloakId(), l4.getReason(), null, "PENDING_TL");
        history("LEAVE", "TL_APPROVED", l4.getId(), users.get("leila.gharbi").getKeycloakId(), "Team leader approved.", "PENDING_TL", "PENDING_HR");
        history("LEAVE", "HR_REJECTED", l4.getId(), hr1.getKeycloakId(), "Insufficient coverage during that period.", "PENDING_HR", "REJECTED");
        notify(users.get("omar.bouzid"), "Leave request rejected", "LEAVE_STATUS", "LEAVE", l4.getId(), "/employee/leaves?leaveId=" + l4.getId());

        // L5 — cancelled by employee (released; no balance effect for Hana)
        LeaveRequest l5 = baseLeave(users.get("hana.nasri"), LeaveType.ANNUAL, past(5), 2,
                "Plans changed.");
        l5.setStatus(LeaveStatus.CANCELLED_BY_EMPLOYEE);
        l5.setTeamLeaderDecision(ApprovalDecision.PENDING);
        l5.setHrDecision(ApprovalDecision.PENDING);
        l5.setCanceledBy(users.get("hana.nasri").getKeycloakId());
        l5.setCanceledAt(LocalDateTime.now().minusWeeks(5).plusDays(1));
        l5 = leaveRequestRepository.save(l5);
        history("LEAVE", "CREATED", l5.getId(), users.get("hana.nasri").getKeycloakId(), l5.getReason(), null, "PENDING_TL");
        history("LEAVE", "EMPLOYEE_CANCELLED", l5.getId(), users.get("hana.nasri").getKeycloakId(), "Employee cancelled request.", "PENDING_TL", "CANCELLED_BY_EMPLOYEE");
        notify(users.get("hana.nasri"), "Leave request cancelled", "LEAVE_STATUS", "LEAVE", l5.getId(), "/employee/leaves?leaveId=" + l5.getId());

        // L6 — AI decision-support case: pending leave overlapping Youssef's task workload (reserves 5)
        LeaveRequest l6 = baseLeave(users.get("youssef.jaziri"), LeaveType.ANNUAL, future(1), 5,
                "Time off while several tasks are in progress.");
        l6.setStatus(LeaveStatus.PENDING);
        l6.setTeamLeaderDecision(ApprovalDecision.PENDING);
        l6.setHrDecision(ApprovalDecision.PENDING);
        l6.setSystemRecommendation("REVIEW");
        l6.setDecisionReason("Employee has active and overdue tasks overlapping the requested window.");
        l6 = leaveRequestRepository.save(l6);
        history("LEAVE", "CREATED", l6.getId(), users.get("youssef.jaziri").getKeycloakId(), l6.getReason(), null, "PENDING_TL");
        notify(users.get("youssef.jaziri"), "Leave request submitted, awaiting team leader review", "LEAVE_STATUS", "LEAVE", l6.getId(), "/employee/leaves?leaveId=" + l6.getId());

        // L7 — Youssef approved history (uses 10)
        LeaveRequest l7 = baseLeave(users.get("youssef.jaziri"), LeaveType.ANNUAL, past(10), 10,
                "Earlier approved annual leave.");
        l7.setStatus(LeaveStatus.APPROVED);
        l7.setTeamLeaderDecision(ApprovalDecision.APPROVED);
        l7.setHrDecision(ApprovalDecision.APPROVED);
        l7.setApprovedBy(hr1.getKeycloakId());
        l7.setApprovalDate(LocalDate.now().minusWeeks(10).plusDays(14));
        l7.setApprovedAt(LocalDateTime.now().minusWeeks(10).plusDays(1));
        l7.setVerificationToken(UUID.randomUUID().toString());
        l7 = leaveRequestRepository.save(l7);
        history("LEAVE", "CREATED", l7.getId(), users.get("youssef.jaziri").getKeycloakId(), l7.getReason(), null, "PENDING_TL");
        history("LEAVE", "TL_APPROVED", l7.getId(), users.get("amine.trabelsi").getKeycloakId(), "Team leader approved.", "PENDING_TL", "PENDING_HR");
        history("LEAVE", "HR_APPROVED", l7.getId(), hr1.getKeycloakId(), "Approved by HR.", "PENDING_HR", "APPROVED");

        // L8 — Salma fully consumes balance (uses 30 -> 0 available)
        LeaveRequest l8 = baseLeave(users.get("salma.ferchichi"), LeaveType.ANNUAL, past(20), 30,
                "Extended approved annual leave.");
        l8.setStatus(LeaveStatus.APPROVED);
        l8.setTeamLeaderDecision(ApprovalDecision.APPROVED);
        l8.setHrDecision(ApprovalDecision.APPROVED);
        l8.setApprovedBy(hr1.getKeycloakId());
        l8.setApprovalDate(LocalDate.now().minusWeeks(20).plusDays(7));
        l8.setApprovedAt(LocalDateTime.now().minusWeeks(20).plusDays(1));
        l8.setVerificationToken(UUID.randomUUID().toString());
        l8 = leaveRequestRepository.save(l8);
        history("LEAVE", "CREATED", l8.getId(), users.get("salma.ferchichi").getKeycloakId(), l8.getReason(), null, "PENDING_TL");
        history("LEAVE", "TL_APPROVED", l8.getId(), users.get("hatem.zouari").getKeycloakId(), "Team leader approved.", "PENDING_TL", "PENDING_HR");
        history("LEAVE", "HR_APPROVED", l8.getId(), hr1.getKeycloakId(), "Approved by HR.", "PENDING_HR", "APPROVED");
    }

    private LeaveRequest baseLeave(User user, LeaveType type, LocalDate start, int workingDays, String reason) {
        LeaveRequest leave = new LeaveRequest();
        leave.setUser(user);
        leave.setLeaveType(type);
        leave.setStartDate(start);
        leave.setEndDate(start.plusDays(Math.max(0, workingDays - 1)));
        leave.setNumberOfDays(workingDays);
        leave.setReason(reason);
        leave.setRequestDate(LocalDateTime.now().minusDays(3));
        leave.setSystemScore(70);
        leave.setSystemRecommendation("APPROVE");
        leave.setDecisionReason("Seeded leave for local PFE demo.");
        return leave;
    }

    // ── Document requests (only LEAVE_BALANCE_STATEMENT is GENERATED) ──
    private void seedDocumentRequests(Map<String, User> users) {
        User hr1 = users.get("nadia.mansouri");
        User hr2 = users.get("sofien.kefi");

        // D1 — approved LEAVE_BALANCE_STATEMENT, GENERATED, system PDF ready (no upload)
        DocumentRequest d1 = new DocumentRequest();
        d1.setUser(users.get("ines.cherif"));
        d1.setDocumentType(DocumentType.LEAVE_BALANCE_STATEMENT);
        d1.setFulfillmentMode(DocumentFulfillmentMode.GENERATED);
        d1.setNotes("Needed for a bank file.");
        d1.setRequestedAt(LocalDateTime.now().minusDays(5));
        d1.setStatus(RequestStatus.APPROVED);
        d1.setApprovedBy(hr1.getKeycloakId());
        d1.setProcessedAt(LocalDateTime.now().minusDays(4));
        d1.setVerificationToken(UUID.randomUUID().toString());
        d1 = documentRequestRepository.save(d1);
        history("DOCUMENT", "CREATED", d1.getId(), users.get("ines.cherif").getKeycloakId(), d1.getNotes(), null, "PENDING");
        history("DOCUMENT", "HR_APPROVED", d1.getId(), hr1.getKeycloakId(), "Generated statement ready.", "PENDING", "APPROVED");
        notify(users.get("ines.cherif"), "Leave balance statement is ready to download", "DOCUMENT_STATUS", "DOCUMENT", d1.getId(), "/employee/documents?requestId=" + d1.getId());

        // D2 — UPLOADED salary certificate, approved but awaiting HR final file (no attachment)
        DocumentRequest d2 = new DocumentRequest();
        d2.setUser(users.get("youssef.jaziri"));
        d2.setDocumentType(DocumentType.SALARY_CERTIFICATE);
        d2.setFulfillmentMode(DocumentFulfillmentMode.UPLOADED);
        d2.setNotes("For apartment rental.");
        d2.setRequestedAt(LocalDateTime.now().minusDays(3));
        d2.setStatus(RequestStatus.APPROVED);
        d2.setApprovedBy(hr2.getKeycloakId());
        d2.setProcessedAt(LocalDateTime.now().minusDays(2));
        d2.setHrNote("Approved — awaiting final file upload.");
        d2 = documentRequestRepository.save(d2);
        history("DOCUMENT", "CREATED", d2.getId(), users.get("youssef.jaziri").getKeycloakId(), d2.getNotes(), null, "PENDING");
        history("DOCUMENT", "HR_APPROVED", d2.getId(), hr2.getKeycloakId(), d2.getHrNote(), "PENDING", "APPROVED");
        notify(users.get("youssef.jaziri"), "Salary certificate approved — HR preparing the file", "DOCUMENT_STATUS", "DOCUMENT", d2.getId(), "/employee/documents?requestId=" + d2.getId());

        // D3 — UPLOADED employment certificate, approved WITH a real placeholder file on disk
        DocumentRequest d3 = new DocumentRequest();
        d3.setUser(users.get("maya.saidi"));
        d3.setDocumentType(DocumentType.EMPLOYMENT_CERTIFICATE);
        d3.setFulfillmentMode(DocumentFulfillmentMode.UPLOADED);
        d3.setNotes("For administrative use.");
        d3.setRequestedAt(LocalDateTime.now().minusDays(6));
        d3.setStatus(RequestStatus.APPROVED);
        d3.setApprovedBy(hr2.getKeycloakId());
        d3.setProcessedAt(LocalDateTime.now().minusDays(5));
        d3.setHrNote("Final signed certificate uploaded.");
        d3 = documentRequestRepository.save(d3);
        attachUploadedFile(d3, hr2.getKeycloakId());
        documentRequestRepository.save(d3);
        history("DOCUMENT", "CREATED", d3.getId(), users.get("maya.saidi").getKeycloakId(), d3.getNotes(), null, "PENDING");
        history("DOCUMENT", "HR_UPLOADED", d3.getId(), hr2.getKeycloakId(), "Final file uploaded.", "PENDING", "APPROVED");
        notify(users.get("maya.saidi"), "Employment certificate is ready to download", "DOCUMENT_STATUS", "DOCUMENT", d3.getId(), "/employee/documents?requestId=" + d3.getId());

        // D4 — rejected document request
        DocumentRequest d4 = new DocumentRequest();
        d4.setUser(users.get("sami.ayari"));
        d4.setDocumentType(DocumentType.WORK_REFERENCE_LETTER);
        d4.setFulfillmentMode(DocumentFulfillmentMode.UPLOADED);
        d4.setNotes("Reference for external application.");
        d4.setRequestedAt(LocalDateTime.now().minusDays(4));
        d4.setStatus(RequestStatus.REJECTED);
        d4.setRejectedBy(hr1.getKeycloakId());
        d4.setProcessedAt(LocalDateTime.now().minusDays(3));
        d4.setHrNote("Please request via your team leader first.");
        d4 = documentRequestRepository.save(d4);
        history("DOCUMENT", "CREATED", d4.getId(), users.get("sami.ayari").getKeycloakId(), d4.getNotes(), null, "PENDING");
        history("DOCUMENT", "HR_REJECTED", d4.getId(), hr1.getKeycloakId(), d4.getHrNote(), "PENDING", "REJECTED");
        notify(users.get("sami.ayari"), "Work reference letter request rejected", "DOCUMENT_STATUS", "DOCUMENT", d4.getId(), "/employee/documents?requestId=" + d4.getId());
    }

    private void attachUploadedFile(DocumentRequest request, String uploadedByKeycloakId) {
        try {
            StoredAttachment attachment = attachmentStorage.store(
                    request.getId(),
                    "employment_certificate.pdf",
                    "application/pdf",
                    placeholderPdf("Employment Certificate - document " + request.getId()));
            request.setAttachmentFileName(attachment.getFileName());
            request.setAttachmentContentType(attachment.getContentType());
            request.setAttachmentStoragePath(attachment.getStoragePath());
            request.setAttachmentSizeBytes((long) attachment.getSizeBytes());
            request.setAttachmentSha256(attachment.getSha256());
            request.setAttachmentUploadedAt(LocalDateTime.now().minusDays(5));
            request.setAttachmentUploadedBy(uploadedByKeycloakId);
        } catch (Exception e) {
            log.warn("FullDemoSeeder could not store placeholder document file: {}", e.getMessage());
        }
    }

    // ── Loan requests (amounts respect the <= 3x monthly salary rule) ──
    private void seedLoanRequests(Map<String, User> users) {
        User hr1 = users.get("nadia.mansouri");
        User hr2 = users.get("sofien.kefi");

        // Ln1 — pending loan (Maya)
        LoanRequest ln1 = baseLoan(users.get("maya.saidi"), LoanType.PERSONAL_ADVANCE, "1500.00", null,
                "Personal advance for a family expense.");
        ln1.setStatus(RequestStatus.PENDING);
        ln1.setSystemRecommendation("APPROVE");
        ln1 = loanRequestRepository.save(ln1);
        history("LOAN", "CREATED", ln1.getId(), users.get("maya.saidi").getKeycloakId(), ln1.getReason(), null, "PENDING");
        notify(users.get("maya.saidi"), "Loan request submitted", "LOAN_STATUS", "LOAN", ln1.getId(), "/employee/loans?requestId=" + ln1.getId());

        // Ln2 — meeting scheduled (Sami)
        LoanRequest ln2 = baseLoan(users.get("sami.ayari"), LoanType.EMERGENCY_LOAN, "3000.00", 12,
                "Emergency family expense.");
        ln2.setStatus(RequestStatus.PENDING);
        ln2.setMeetingRequired(true);
        ln2.setMeetingAt(LocalDateTime.now().plusDays(4).withHour(10).withMinute(0).withSecond(0).withNano(0));
        ln2.setMeetingNote("Discuss repayment schedule.");
        ln2.setMeetingScheduledBy(hr1.getKeycloakId());
        ln2.setMeetingScheduledAt(LocalDateTime.now().minusDays(1));
        ln2.setSystemRecommendation("REVIEW");
        ln2 = loanRequestRepository.save(ln2);
        history("LOAN", "CREATED", ln2.getId(), users.get("sami.ayari").getKeycloakId(), ln2.getReason(), null, "PENDING");
        history("LOAN", "MEETING_SCHEDULED", ln2.getId(), hr1.getKeycloakId(), ln2.getMeetingNote(), "PENDING", "MEETING_SCHEDULED");
        notify(users.get("sami.ayari"), "Loan review meeting scheduled", "LOAN_STATUS", "LOAN", ln2.getId(), "/employee/loans?requestId=" + ln2.getId());

        // Ln3 — approved loan with verification token (PDF + QR works) (Ines)
        LoanRequest ln3 = baseLoan(users.get("ines.cherif"), LoanType.EDUCATION_LOAN, "4000.00", 18,
                "Professional certification funding.");
        ln3.setStatus(RequestStatus.APPROVED);
        ln3.setApprovedBy(hr1.getKeycloakId());
        ln3.setApprovedAt(LocalDateTime.now().minusDays(2));
        ln3.setProcessedAt(LocalDateTime.now().minusDays(2));
        ln3.setApprovedAmount(new BigDecimal("4000.00"));
        ln3.setApprovedAmountJustification("Full approval.");
        ln3.setVerificationToken(UUID.randomUUID().toString());
        ln3 = loanRequestRepository.save(ln3);
        history("LOAN", "CREATED", ln3.getId(), users.get("ines.cherif").getKeycloakId(), ln3.getReason(), null, "PENDING");
        history("LOAN", "HR_APPROVED", ln3.getId(), hr1.getKeycloakId(), "Approved by HR.", "PENDING", "APPROVED");
        notify(users.get("ines.cherif"), "Loan approved — certificate available", "LOAN_STATUS", "LOAN", ln3.getId(), "/employee/loans?requestId=" + ln3.getId());

        // Ln4 — rejected loan (Omar)
        LoanRequest ln4 = baseLoan(users.get("omar.bouzid"), LoanType.HOUSING_ADVANCE, "6000.00", 24,
                "Housing advance request.");
        ln4.setStatus(RequestStatus.REJECTED);
        ln4.setRejectedBy(hr2.getKeycloakId());
        ln4.setRejectedAt(LocalDateTime.now().minusDays(2));
        ln4.setProcessedAt(LocalDateTime.now().minusDays(2));
        ln4.setHrDecisionReason("Existing commitments exceed eligibility this quarter.");
        ln4 = loanRequestRepository.save(ln4);
        history("LOAN", "CREATED", ln4.getId(), users.get("omar.bouzid").getKeycloakId(), ln4.getReason(), null, "PENDING");
        history("LOAN", "HR_REJECTED", ln4.getId(), hr2.getKeycloakId(), ln4.getHrDecisionReason(), "PENDING", "REJECTED");
        notify(users.get("omar.bouzid"), "Loan request rejected", "LOAN_STATUS", "LOAN", ln4.getId(), "/employee/loans?requestId=" + ln4.getId());
    }

    private LoanRequest baseLoan(User user, LoanType type, String amount, Integer months, String reason) {
        LoanRequest loan = new LoanRequest();
        loan.setUser(user);
        loan.setLoanType(type);
        loan.setAmount(new BigDecimal(amount).setScale(2, RoundingMode.HALF_UP));
        loan.setRepaymentMonths(months);
        loan.setReason(reason);
        loan.setRequestedAt(LocalDateTime.now().minusDays(5));
        if (months != null && months > 0) {
            loan.setMonthlyInstallment(loan.getAmount().divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP));
        }
        loan.setRiskScore(45);
        loan.setDecisionReason("Seeded loan evaluation for local PFE demo.");
        loan.setMeetingRequired(false);
        return loan;
    }

    // ── Authorization requests ──
    private void seedAuthorizationRequests(Map<String, User> users) {
        User hr1 = users.get("nadia.mansouri");
        User hr2 = users.get("sofien.kefi");

        // A1 — pending TIME_PERMISSION (Youssef)
        AuthorizationRequest a1 = new AuthorizationRequest();
        a1.setUser(users.get("youssef.jaziri"));
        a1.setAuthorizationType(AuthorizationType.TIME_PERMISSION);
        a1.setReason("Medical appointment in the morning.");
        a1.setRequestedAt(LocalDateTime.now().minusDays(1));
        a1.setAbsenceDate(future(1));
        a1.setFromTime(LocalTime.of(9, 0));
        a1.setToTime(LocalTime.of(11, 0));
        a1.setStatus(RequestStatus.PENDING);
        a1 = authorizationRequestRepository.save(a1);
        history("AUTH", "CREATED", a1.getId(), users.get("youssef.jaziri").getKeycloakId(), a1.getReason(), null, "PENDING");
        notify(users.get("youssef.jaziri"), "Time permission request submitted", "AUTH_STATUS", "AUTH", a1.getId(), "/employee/authorizations?requestId=" + a1.getId());

        // A2 — approved EQUIPMENT_REQUEST (Maya)
        AuthorizationRequest a2 = new AuthorizationRequest();
        a2.setUser(users.get("maya.saidi"));
        a2.setAuthorizationType(AuthorizationType.EQUIPMENT_REQUEST);
        a2.setReason("Need a laptop for remote testing.");
        a2.setRequestedAt(LocalDateTime.now().minusDays(3));
        a2.setStartDate(future(1));
        a2.setEndDate(future(1).plusDays(10));
        a2.setEquipmentType("Laptop");
        a2.setStatus(RequestStatus.APPROVED);
        a2.setApprovedBy(hr1.getKeycloakId());
        a2.setProcessedAt(LocalDateTime.now().minusDays(2));
        a2.setVerificationToken(UUID.randomUUID().toString());
        a2 = authorizationRequestRepository.save(a2);
        history("AUTH", "CREATED", a2.getId(), users.get("maya.saidi").getKeycloakId(), a2.getReason(), null, "PENDING");
        history("AUTH", "HR_APPROVED", a2.getId(), hr1.getKeycloakId(), "Approved equipment request.", "PENDING", "APPROVED");
        notify(users.get("maya.saidi"), "Equipment request approved", "AUTH_STATUS", "AUTH", a2.getId(), "/employee/authorizations?requestId=" + a2.getId());

        // A3 — rejected TIME_PERMISSION (Sami)
        AuthorizationRequest a3 = new AuthorizationRequest();
        a3.setUser(users.get("sami.ayari"));
        a3.setAuthorizationType(AuthorizationType.TIME_PERMISSION);
        a3.setReason("Personal errand in the afternoon.");
        a3.setRequestedAt(LocalDateTime.now().minusDays(2));
        a3.setAbsenceDate(future(2));
        a3.setFromTime(LocalTime.of(14, 0));
        a3.setToTime(LocalTime.of(16, 0));
        a3.setStatus(RequestStatus.REJECTED);
        a3.setRejectedBy(hr2.getKeycloakId());
        a3.setProcessedAt(LocalDateTime.now().minusDays(1));
        a3.setHrNote("Coverage required during that window.");
        a3 = authorizationRequestRepository.save(a3);
        history("AUTH", "CREATED", a3.getId(), users.get("sami.ayari").getKeycloakId(), a3.getReason(), null, "PENDING");
        history("AUTH", "HR_REJECTED", a3.getId(), hr2.getKeycloakId(), a3.getHrNote(), "PENDING", "REJECTED");
        notify(users.get("sami.ayari"), "Time permission request rejected", "AUTH_STATUS", "AUTH", a3.getId(), "/employee/authorizations?requestId=" + a3.getId());
    }

    // ── Projects & tasks (4 projects, one per team; 16 tasks with full variety) ──
    private void seedProjectsAndTasks(Map<String, User> users, Map<String, Team> teams) {
        Project eng = createProject(teams.get("Engineering"), "ArabSoft Platform Hardening",
                "Stability, performance, and reporting improvements for the ArabSoft platform.");
        Project sup = createProject(teams.get("Support"), "Support Desk Revamp",
                "Improve ticket handling, knowledge base, and customer response times.");
        Project fin = createProject(teams.get("Finance"), "Payroll Automation",
                "Automate payroll reconciliation and quarterly financial reporting.");
        Project ops = createProject(teams.get("Operations"), "Logistics Optimization",
                "Vendor onboarding, inventory audits, and office logistics.");

        // Engineering — Youssef carries the AI decision-support workload evidence.
        task(eng, users.get("youssef.jaziri"), "Fix login token refresh", TaskStatus.IN_PROGRESS, TaskPriority.HIGH, -10, -5, true);
        task(eng, users.get("youssef.jaziri"), "Refactor dashboard charts", TaskStatus.IN_PROGRESS, TaskPriority.HIGH, -3, 10, false);
        task(eng, users.get("youssef.jaziri"), "Patch PDF export styling", TaskStatus.TODO, TaskPriority.MEDIUM, 0, 3, false);
        task(eng, users.get("ines.cherif"), "Implement leave balance API cache", TaskStatus.IN_PROGRESS, TaskPriority.HIGH, -2, 12, false);
        task(eng, users.get("ines.cherif"), "Write integration tests", TaskStatus.DONE, TaskPriority.MEDIUM, -14, -2, false);
        task(eng, users.get("maya.saidi"), "QA regression suite", TaskStatus.TODO, TaskPriority.MEDIUM, -1, 8, false);
        task(eng, users.get("maya.saidi"), "Sign off test plan", TaskStatus.DONE, TaskPriority.LOW, -10, -1, false);

        // Support
        task(sup, users.get("sami.ayari"), "Resolve ticket backlog", TaskStatus.IN_PROGRESS, TaskPriority.MEDIUM, -4, 6, false);
        task(sup, users.get("sami.ayari"), "Escalation process redesign", TaskStatus.IN_PROGRESS, TaskPriority.HIGH, -2, 5, false);
        task(sup, users.get("omar.bouzid"), "Update knowledge base", TaskStatus.TODO, TaskPriority.LOW, 0, 2, false);
        task(sup, users.get("hana.nasri"), "Customer survey rollout", TaskStatus.DONE, TaskPriority.MEDIUM, -12, -3, false);

        // Finance
        task(fin, users.get("salma.ferchichi"), "Payroll reconciliation", TaskStatus.IN_PROGRESS, TaskPriority.HIGH, -3, 9, false);
        task(fin, users.get("fares.amri"), "Quarterly expense report", TaskStatus.TODO, TaskPriority.MEDIUM, 0, 14, false);

        // Operations
        task(ops, users.get("lina.brahmi"), "Vendor onboarding", TaskStatus.IN_PROGRESS, TaskPriority.MEDIUM, -2, 7, false);
        task(ops, users.get("tarek.kacem"), "Inventory audit", TaskStatus.TODO, TaskPriority.MEDIUM, 0, 4, false);
        task(ops, users.get("tarek.kacem"), "Office relocation plan", TaskStatus.DONE, TaskPriority.LOW, -16, -6, false);
    }

    private Project createProject(Team team, String name, String description) {
        Project project = new Project();
        project.setTeam(team);
        project.setName(name);
        project.setDescription(description);
        return projectRepository.save(project);
    }

    private void task(Project project, User assignee, String title, TaskStatus status, TaskPriority priority,
                      int startOffsetDays, int dueOffsetDays, boolean notifyAssignee) {
        Task task = new Task();
        task.setProject(project);
        task.setAssignee(assignee);
        task.setTitle(title);
        task.setDescription("Seeded task for local PFE demo.");
        task.setStatus(status);
        task.setPriority(priority);
        task.setStartDate(LocalDate.now().plusDays(startOffsetDays));
        task.setDueDate(LocalDate.now().plusDays(dueOffsetDays));
        Task saved = taskRepository.save(task);
        if (notifyAssignee && assignee != null) {
            notify(assignee, "New task assigned: " + saved.getTitle(), "TASK_ASSIGNED", "TASK", saved.getId(), "/employee/tasks?taskId=" + saved.getId());
        }
    }

    // ── HR manual email audit (so "Sent HR Emails" is not empty) ──
    private void seedHrManualEmails(Map<String, User> users) {
        emailLog(users.get("nadia.mansouri"), users.get("ines.cherif"),
                "Your leave balance statement is ready",
                "Hello Ines, your requested leave balance statement has been generated and is ready to download.",
                "DOCUMENT");
        emailLog(users.get("nadia.mansouri"), users.get("omar.bouzid"),
                "Update on your leave request",
                "Hello Omar, your recent leave request could not be approved due to coverage constraints.",
                "LEAVE");
        emailLog(users.get("sofien.kefi"), users.get("youssef.jaziri"),
                "Salary certificate in preparation",
                "Hello Youssef, your salary certificate has been approved and HR is preparing the final file.",
                "DOCUMENT");
    }

    private void emailLog(User sender, User recipient, String subject, String body, String referenceType) {
        HrManualEmailLog logRow = new HrManualEmailLog();
        logRow.setRecipientUserId(recipient.getId());
        logRow.setRecipientEmail(recipient.getPerson().getEmail());
        logRow.setSentByUserId(sender.getId());
        logRow.setSentByUsername(sender.getUsername());
        logRow.setSentByDisplayName(sender.getPerson().getFirstName() + " " + sender.getPerson().getLastName());
        logRow.setSubject(subject);
        logRow.setBodyPreview(body);
        logRow.setReferenceType(referenceType);
        logRow.setStatus(HrManualEmailStatus.SENT);
        logRow.setCreatedAt(LocalDateTime.now().minusDays(1));
        logRow.setSentAt(LocalDateTime.now().minusDays(1));
        hrManualEmailLogRepository.save(logRow);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Small helpers
    // ──────────────────────────────────────────────────────────────────────────
    private void history(String type, String action, Long requestId, String actorId, String comment,
                         String fromState, String toState) {
        requestHistoryRepository.save(new RequestHistory(requestId, type, action, actorId, comment, fromState, toState));
    }

    private void notify(User user, String message, String type, String referenceType, Long referenceId, String actionUrl) {
        notificationRepository.save(new Notification(user, message, type, referenceType, referenceId, actionUrl));
    }

    private LocalDate future(int weeks) {
        return LocalDate.now().plusWeeks(weeks);
    }

    private LocalDate past(int weeks) {
        return LocalDate.now().minusWeeks(weeks);
    }

    private byte[] placeholderPdf(String label) {
        String content = "%PDF-1.4\n% ArabSoft demo placeholder: " + label + "\n";
        return content.getBytes(StandardCharsets.UTF_8);
    }
}
