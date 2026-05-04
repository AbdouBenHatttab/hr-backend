package tn.isetbizerte.pfe.hrbackend.devdata;

import jakarta.persistence.EntityManager;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
import tn.isetbizerte.pfe.hrbackend.modules.employee.entity.LeavePolicy;
import tn.isetbizerte.pfe.hrbackend.modules.employee.entity.LeaveRequest;
import tn.isetbizerte.pfe.hrbackend.modules.employee.repository.EmployeeLeaveBalanceRepository;
import tn.isetbizerte.pfe.hrbackend.modules.employee.repository.LeavePolicyRepository;
import tn.isetbizerte.pfe.hrbackend.modules.employee.repository.LeaveRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.history.entity.RequestHistory;
import tn.isetbizerte.pfe.hrbackend.modules.history.repository.RequestHistoryRepository;
import tn.isetbizerte.pfe.hrbackend.modules.hr.service.KeycloakAdminService;
import tn.isetbizerte.pfe.hrbackend.modules.jobtitle.entity.JobTitle;
import tn.isetbizerte.pfe.hrbackend.modules.jobtitle.repository.JobTitleRepository;
import tn.isetbizerte.pfe.hrbackend.modules.notification.entity.Notification;
import tn.isetbizerte.pfe.hrbackend.modules.notification.repository.NotificationRepository;
import tn.isetbizerte.pfe.hrbackend.modules.requests.entity.AuthorizationRequest;
import tn.isetbizerte.pfe.hrbackend.modules.requests.entity.DocumentRequest;
import tn.isetbizerte.pfe.hrbackend.modules.requests.entity.LoanRequest;
import tn.isetbizerte.pfe.hrbackend.modules.requests.entity.StoredEmployeeDocument;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.AuthorizationRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.DocumentRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.LoanRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.StoredEmployeeDocumentRepository;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DevDataSeederService {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeederService.class);
    private static final String REQUIRED_CONFIRM = "seed.arabsoft.local";
    private static final String REQUIRED_USERNAME_PREFIX = "seed.";
    private static final String REQUIRED_EMAIL_DOMAIN = "seed.arabsoft.local";
    private static final String SEED_MARKER = "[seed-dev-data]";
    private static final String TEAM_PREFIX = "Seed ";
    private static final List<String> ROLES = List.of("HR_MANAGER", "TEAM_LEADER", "EMPLOYEE", "NEW_USER");

    private final DevSeedProperties properties;
    private final Environment environment;
    private final String datasourceUrl;
    private final String keycloakUrl;
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
    private final LeavePolicyRepository leavePolicyRepository;
    private final EmployeeLeaveBalanceRepository leaveBalanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final DocumentRequestRepository documentRequestRepository;
    private final LoanRequestRepository loanRequestRepository;
    private final AuthorizationRequestRepository authorizationRequestRepository;
    private final StoredEmployeeDocumentRepository storedDocumentRepository;
    private final RequestHistoryRepository requestHistoryRepository;
    private final NotificationRepository notificationRepository;
    private final DocumentAttachmentStorageService attachmentStorage;
    private final SeedDatasetFactory datasetFactory = new SeedDatasetFactory();

    public DevDataSeederService(DevSeedProperties properties,
                                Environment environment,
                                @Value("${spring.datasource.url}") String datasourceUrl,
                                @Value("${keycloak.auth-server-url}") String keycloakUrl,
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
                                LeavePolicyRepository leavePolicyRepository,
                                EmployeeLeaveBalanceRepository leaveBalanceRepository,
                                LeaveRequestRepository leaveRequestRepository,
                                DocumentRequestRepository documentRequestRepository,
                                LoanRequestRepository loanRequestRepository,
                                AuthorizationRequestRepository authorizationRequestRepository,
                                StoredEmployeeDocumentRepository storedDocumentRepository,
                                RequestHistoryRepository requestHistoryRepository,
                                NotificationRepository notificationRepository,
                                DocumentAttachmentStorageService attachmentStorage) {
        this.properties = properties;
        this.environment = environment;
        this.datasourceUrl = datasourceUrl;
        this.keycloakUrl = keycloakUrl;
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
        this.leavePolicyRepository = leavePolicyRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.documentRequestRepository = documentRequestRepository;
        this.loanRequestRepository = loanRequestRepository;
        this.authorizationRequestRepository = authorizationRequestRepository;
        this.storedDocumentRepository = storedDocumentRepository;
        this.requestHistoryRepository = requestHistoryRepository;
        this.notificationRepository = notificationRepository;
        this.attachmentStorage = attachmentStorage;
    }

    public void resetAndSeed() {
        validateSafetyGuards();
        if (!keycloakAdminService.ensureRealmRolesExist(ROLES)) {
            throw new IllegalStateException("Cannot seed dev data because Keycloak realm roles are unavailable.");
        }

        ResetSnapshot snapshot = tx.execute(status -> collectResetSnapshot());
        deleteSeededKeycloakUsers(snapshot);
        tx.executeWithoutResult(status -> resetPostgreSql(snapshot));
        deleteSeededUploads(snapshot.attachmentPaths());

        List<SeedUserSpec> specs = datasetFactory.users(properties.getUsernamePrefix(), properties.getEmailDomain());
        Map<String, String> keycloakIds = createSeededKeycloakUsers(specs);
        tx.executeWithoutResult(status -> seedPostgreSql(specs, keycloakIds));
        log.info("Local dev seed completed: {} users, {} teams, {} projects, {} tasks.",
                specs.size(), 10, 20, 150);
    }

    private void validateSafetyGuards() {
        List<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
        require(activeProfiles.contains("dev-seed"), "active profile must contain dev-seed");
        require(properties.isEnabled(), "app.dev-seed.enabled must be true");
        require(properties.isReset(), "app.dev-seed.reset must be true");
        require(REQUIRED_CONFIRM.equals(properties.getConfirm()), "app.dev-seed.confirm must be seed.arabsoft.local");
        require(REQUIRED_USERNAME_PREFIX.equals(properties.getUsernamePrefix()), "app.dev-seed.username-prefix must be seed.");
        require(REQUIRED_EMAIL_DOMAIN.equals(properties.getEmailDomain()), "app.dev-seed.email-domain must be seed.arabsoft.local");
        require(properties.getPassword() != null && !properties.getPassword().isBlank(), "app.dev-seed.password must not be blank");
        if (properties.isRequireLocalDatabase()) {
            require(isLocalUrl(datasourceUrl), "spring.datasource.url must point to a local database");
        }
        if (properties.isRequireLocalKeycloak()) {
            require(isLocalUrl(keycloakUrl), "keycloak.auth-server-url must point to local Keycloak");
        }
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Dev data seeder refused to run: " + message);
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

    private ResetSnapshot collectResetSnapshot() {
        List<User> users = userRepository.findAll().stream()
                .filter(this::isSeededUser)
                .toList();
        List<Long> userIds = users.stream().map(User::getId).filter(Objects::nonNull).toList();
        List<Long> personIds = users.stream()
                .map(User::getPerson)
                .filter(Objects::nonNull)
                .map(Person::getId)
                .filter(Objects::nonNull)
                .toList();
        Set<String> keycloakIds = users.stream()
                .map(User::getKeycloakId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toCollection(HashSet::new));

        List<Long> teamIds = selectLongs("select t.id from Team t where t.name like :prefix", "prefix", TEAM_PREFIX + "%");
        if (!userIds.isEmpty()) {
            teamIds = new ArrayList<>(new HashSet<>(teamIds));
            teamIds.addAll(selectLongs("select t.id from Team t where t.teamLeader.id in :ids", "ids", userIds));
            teamIds = new ArrayList<>(new HashSet<>(teamIds));
        }

        List<Long> leaveIds = userIds.isEmpty() ? List.of() : selectLongs("select r.id from LeaveRequest r where r.user.id in :ids", "ids", userIds);
        List<Long> documentIds = userIds.isEmpty() ? List.of() : selectLongs("select r.id from DocumentRequest r where r.user.id in :ids", "ids", userIds);
        List<Long> loanIds = userIds.isEmpty() ? List.of() : selectLongs("select r.id from LoanRequest r where r.user.id in :ids", "ids", userIds);
        List<Long> authorizationIds = userIds.isEmpty() ? List.of() : selectLongs("select r.id from AuthorizationRequest r where r.user.id in :ids", "ids", userIds);
        List<Long> taskIds = selectLongs("select t.id from Task t where t.project.team.name like :prefix", "prefix", TEAM_PREFIX + "%");

        Set<String> attachmentPaths = new HashSet<>();
        collectAttachmentPaths("select r.attachmentStoragePath from DocumentRequest r where r.attachmentStoragePath is not null and r.user.id in :ids", userIds, attachmentPaths);
        collectAttachmentPaths("select r.attachmentStoragePath from LoanRequest r where r.attachmentStoragePath is not null and r.user.id in :ids", userIds, attachmentPaths);
        collectAttachmentPaths("select r.storagePath from StoredEmployeeDocument r where r.storagePath is not null and r.employee.id in :ids", userIds, attachmentPaths);

        return new ResetSnapshot(userIds, personIds, new ArrayList<>(keycloakIds), teamIds, leaveIds, documentIds,
                loanIds, authorizationIds, taskIds, new ArrayList<>(attachmentPaths));
    }

    private boolean isSeededUser(User user) {
        if (user == null) {
            return false;
        }
        boolean seededUsername = user.getUsername() != null && user.getUsername().startsWith(properties.getUsernamePrefix());
        boolean seededEmail = user.getPerson() != null
                && user.getPerson().getEmail() != null
                && user.getPerson().getEmail().endsWith("@" + properties.getEmailDomain());
        return seededUsername || seededEmail;
    }

    private List<Long> selectLongs(String jpql, String parameter, Object value) {
        if (value instanceof List<?> list && list.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery(jpql, Long.class)
                .setParameter(parameter, value)
                .getResultList();
    }

    private void collectAttachmentPaths(String jpql, List<Long> userIds, Set<String> paths) {
        if (userIds.isEmpty()) {
            return;
        }
        entityManager.createQuery(jpql, String.class)
                .setParameter("ids", userIds)
                .getResultStream()
                .filter(path -> path != null && !path.isBlank())
                .forEach(paths::add);
    }

    private void deleteSeededKeycloakUsers(ResetSnapshot snapshot) {
        Set<String> keycloakIds = new HashSet<>(snapshot.keycloakIds());
        for (UserRepresentation user : keycloakAdminService.findUsersByUsernamePrefix(properties.getUsernamePrefix())) {
            if (user.getId() != null && user.getUsername() != null && user.getUsername().startsWith(properties.getUsernamePrefix())) {
                keycloakIds.add(user.getId());
            }
        }
        for (String keycloakId : keycloakIds) {
            if (!keycloakAdminService.deleteUserById(keycloakId)) {
                throw new IllegalStateException("Stopped before PostgreSQL cleanup because Keycloak deletion failed for " + keycloakId);
            }
        }
    }

    private void resetPostgreSql(ResetSnapshot snapshot) {
        List<Long> userIds = snapshot.userIds();
        List<Long> teamIds = snapshot.teamIds();

        deleteNotifications(snapshot);
        deleteHistory("LEAVE", snapshot.leaveIds());
        deleteHistory("DOCUMENT", snapshot.documentIds());
        deleteHistory("LOAN", snapshot.loanIds());
        deleteHistory("AUTH", snapshot.authorizationIds());

        bulkDelete("delete from Task t where t.id in :ids", snapshot.taskIds());
        if (!teamIds.isEmpty()) {
            bulkDelete("delete from Task t where t.project.id in (select p.id from Project p where p.team.id in :ids)", teamIds);
            bulkDelete("delete from Project p where p.team.id in :ids", teamIds);
        }
        if (!userIds.isEmpty()) {
            bulkDelete("delete from EmployeeLeaveBalance b where b.user.id in :ids", userIds);
            bulkDelete("delete from LeaveRequest r where r.user.id in :ids", userIds);
            bulkDelete("delete from LoanRequest r where r.user.id in :ids", userIds);
            bulkDelete("delete from DocumentRequest r where r.user.id in :ids", userIds);
            bulkDelete("delete from AuthorizationRequest r where r.user.id in :ids", userIds);
            bulkDelete("delete from StoredEmployeeDocument d where d.employee.id in :ids", userIds);
            bulkDelete("delete from PasswordResetToken t where t.userId in :ids", userIds);
            bulkDelete("delete from PasswordChangeToken t where t.userId in :ids", userIds);
            bulkDelete("delete from LoginHistory h where h.user.id in :ids", userIds);
            entityManager.createQuery("update User u set u.team = null where u.id in :ids")
                    .setParameter("ids", userIds)
                    .executeUpdate();
        }
        if (!teamIds.isEmpty()) {
            entityManager.createQuery("update Team t set t.teamLeader = null where t.id in :ids")
                    .setParameter("ids", teamIds)
                    .executeUpdate();
            bulkDelete("delete from Team t where t.id in :ids", teamIds);
        }
        if (!userIds.isEmpty()) {
            bulkDelete("delete from User u where u.id in :ids", userIds);
        }
        deleteSeededOrphanPersons(snapshot.personIds());
        deleteSeededReferenceData();
    }

    private void deleteNotifications(ResetSnapshot snapshot) {
        if (!snapshot.userIds().isEmpty()) {
            bulkDelete("delete from Notification n where n.user.id in :ids", snapshot.userIds());
        }
        deleteNotificationsByReference("LEAVE", snapshot.leaveIds());
        deleteNotificationsByReference("DOCUMENT", snapshot.documentIds());
        deleteNotificationsByReference("LOAN", snapshot.loanIds());
        deleteNotificationsByReference("AUTH", snapshot.authorizationIds());
        deleteNotificationsByReference("TASK", snapshot.taskIds());
    }

    private void deleteNotificationsByReference(String type, List<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        entityManager.createQuery("delete from Notification n where upper(n.referenceType) = :type and n.referenceId in :ids")
                .setParameter("type", type)
                .setParameter("ids", ids)
                .executeUpdate();
    }

    private void deleteHistory(String type, List<Long> requestIds) {
        if (requestIds.isEmpty()) {
            return;
        }
        entityManager.createQuery("delete from RequestHistory h where h.type = :type and h.requestId in :ids")
                .setParameter("type", type)
                .setParameter("ids", requestIds)
                .executeUpdate();
    }

    private void bulkDelete(String jpql, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        entityManager.createQuery(jpql)
                .setParameter("ids", ids)
                .executeUpdate();
    }

    private void deleteSeededOrphanPersons(List<Long> personIds) {
        if (!personIds.isEmpty()) {
            entityManager.createQuery("""
                            delete from Person p
                            where p.id in :ids
                            and not exists (select u.id from User u where u.person = p)
                            """)
                    .setParameter("ids", personIds)
                    .executeUpdate();
        }
        entityManager.createQuery("""
                        delete from Person p
                        where lower(p.email) like :emailPattern
                        and not exists (select u.id from User u where u.person = p)
                        """)
                .setParameter("emailPattern", "%@" + properties.getEmailDomain().toLowerCase(Locale.ROOT))
                .executeUpdate();
    }

    private void deleteSeededReferenceData() {
        List<Long> departmentIds = selectLongs("""
                        select d.id from Department d
                        where d.description like :marker
                        and not exists (select p.id from Person p where p.departmentRef = d)
                        """, "marker", "%" + SEED_MARKER + "%");
        bulkDelete("delete from Department d where d.id in :ids", departmentIds);

        List<Long> jobTitleIds = selectLongs("""
                        select j.id from JobTitle j
                        where j.description like :marker
                        and not exists (select p.id from Person p where p.jobTitleRef = j)
                        """, "marker", "%" + SEED_MARKER + "%");
        bulkDelete("delete from JobTitle j where j.id in :ids", jobTitleIds);
    }

    private void deleteSeededUploads(List<String> attachmentPaths) {
        for (String path : attachmentPaths) {
            try {
                attachmentStorage.deleteIfExists(path);
            } catch (Exception e) {
                log.warn("Seed cleanup could not delete attachment '{}': {}", path, e.getMessage());
            }
        }
    }

    private Map<String, String> createSeededKeycloakUsers(List<SeedUserSpec> specs) {
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
                throw new IllegalStateException("Failed to create seeded Keycloak user " + spec.username());
            }
            if (!keycloakAdminService.assignRoleToUser(id, spec.role().name())) {
                throw new IllegalStateException("Failed to assign Keycloak role for " + spec.username());
            }
            ids.put(spec.username(), id);
        }
        return ids;
    }

    private void seedPostgreSql(List<SeedUserSpec> specs, Map<String, String> keycloakIds) {
        Map<String, Department> departments = ensureDepartments();
        Map<String, JobTitle> jobTitles = ensureJobTitles();
        Map<String, User> users = createLocalUsers(specs, keycloakIds, departments, jobTitles);
        List<Team> teams = createTeams(users);
        assignEmployeesToTeams(users, teams);
        createLeavePoliciesAndBalances(users.values());
        createRequests(users);
        createProjectsAndTasks(teams);
        entityManager.flush();
    }

    private Map<String, Department> ensureDepartments() {
        Map<String, Department> departments = new HashMap<>();
        for (String name : SeedDatasetFactory.DEPARTMENTS) {
            Department department = departmentRepository.findByNameIgnoreCase(name)
                    .orElseGet(() -> {
                        Department d = new Department();
                        d.setName(name);
                        d.setDescription("Seed reference department " + SEED_MARKER);
                        d.setActive(true);
                        return departmentRepository.save(d);
                    });
            departments.put(name, department);
        }
        return departments;
    }

    private Map<String, JobTitle> ensureJobTitles() {
        Map<String, JobTitle> jobTitles = new HashMap<>();
        for (Map.Entry<String, BigDecimal> entry : SeedDatasetFactory.JOB_TITLES.entrySet()) {
            JobTitle jobTitle = jobTitleRepository.findByNameIgnoreCase(entry.getKey())
                    .orElseGet(() -> {
                        JobTitle j = new JobTitle();
                        j.setName(entry.getKey());
                        j.setDescription("Seed reference job title " + SEED_MARKER);
                        j.setActive(true);
                        j.setDefaultSalary(entry.getValue());
                        return jobTitleRepository.save(j);
                    });
            jobTitles.put(entry.getKey(), jobTitle);
        }
        return jobTitles;
    }

    private Map<String, User> createLocalUsers(List<SeedUserSpec> specs,
                                               Map<String, String> keycloakIds,
                                               Map<String, Department> departments,
                                               Map<String, JobTitle> jobTitles) {
        Map<String, User> users = new LinkedHashMap<>();
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
            person.setAvatarColor(avatarColor(users.size()));

            User user = new User(keycloakIds.get(spec.username()), spec.username());
            user.setRole(spec.role());
            user.setActive(spec.role() != TypeRole.NEW_USER);
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

    private List<Team> createTeams(Map<String, User> users) {
        List<User> leaders = users.values().stream()
                .filter(user -> user.getRole() == TypeRole.TEAM_LEADER)
                .sorted(Comparator.comparing(User::getUsername))
                .toList();
        List<Team> teams = new ArrayList<>();
        for (int i = 0; i < leaders.size(); i++) {
            Team team = new Team();
            team.setName(TEAM_PREFIX + String.format("%02d", i + 1) + " - " + teamName(i));
            team.setDescription("Seeded local development team " + SEED_MARKER);
            team.setMaxLeavePercentage(35 + (i % 3) * 5);
            team.setTeamLeader(leaders.get(i));
            teams.add(teamRepository.save(team));
        }
        return teams;
    }

    private String teamName(int index) {
        List<String> names = List.of("Platform", "Experience", "Quality", "People", "Finance", "Care", "Delivery", "Data", "Mobile", "Cloud");
        return names.get(index % names.size());
    }

    private void assignEmployeesToTeams(Map<String, User> users, List<Team> teams) {
        List<User> employees = users.values().stream()
                .filter(user -> user.getRole() == TypeRole.EMPLOYEE)
                .sorted(Comparator.comparing(User::getUsername))
                .toList();
        for (int i = 0; i < employees.size(); i++) {
            employees.get(i).setTeam(teams.get(i % teams.size()));
            userRepository.save(employees.get(i));
        }
    }

    private void createLeavePoliciesAndBalances(Iterable<User> users) {
        Map<LeaveType, LeavePolicy> policies = new HashMap<>();
        for (LeaveType type : LeaveType.values()) {
            policies.put(type, leavePolicyRepository.findByLeaveType(type)
                    .orElseGet(() -> leavePolicyRepository.save(defaultPolicy(type))));
        }

        int year = LocalDate.now().getYear();
        for (User user : users) {
            if (!Boolean.TRUE.equals(user.getActive())) {
                continue;
            }
            for (LeaveType type : LeaveType.values()) {
                LeavePolicy policy = policies.get(type);
                EmployeeLeaveBalance balance = new EmployeeLeaveBalance(user, type, year,
                        BigDecimal.valueOf(policy.getAnnualAllowanceDays()));
                balance.setUsedDays(BigDecimal.valueOf((user.getId() + type.ordinal()) % 5));
                balance.setReservedDays(BigDecimal.valueOf((user.getId() + type.ordinal()) % 3));
                balance.setLastAccruedMonth(LocalDate.now().getMonthValue());
                leaveBalanceRepository.save(balance);
            }
        }
    }

    private LeavePolicy defaultPolicy(LeaveType type) {
        LeavePolicy policy = new LeavePolicy();
        policy.setLeaveType(type);
        policy.setActive(true);
        policy.setBalanceManaged(type != LeaveType.UNPAID && type != LeaveType.OTHER);
        policy.setAccrualManaged(type == LeaveType.ANNUAL);
        policy.setAnnualAllowanceDays(switch (type) {
            case ANNUAL -> 24;
            case SICK -> 12;
            case MATERNITY -> 60;
            case PATERNITY -> 7;
            default -> 0;
        });
        policy.setMonthlyAccrualDays(type == LeaveType.ANNUAL ? new BigDecimal("2.00") : BigDecimal.ZERO);
        policy.setCarryForwardCapDays(type == LeaveType.ANNUAL ? new BigDecimal("5.00") : BigDecimal.ZERO);
        return policy;
    }

    private void createRequests(Map<String, User> users) {
        List<User> activeRequesters = users.values().stream()
                .filter(user -> Boolean.TRUE.equals(user.getActive()))
                .filter(user -> user.getRole() == TypeRole.EMPLOYEE || user.getRole() == TypeRole.TEAM_LEADER)
                .sorted(Comparator.comparing(User::getUsername))
                .toList();
        List<User> hrUsers = users.values().stream()
                .filter(user -> user.getRole() == TypeRole.HR_MANAGER)
                .sorted(Comparator.comparing(User::getUsername))
                .toList();

        for (int i = 0; i < 50; i++) {
            createLeaveRequest(activeRequesters.get(i % activeRequesters.size()), hrUsers.get(i % hrUsers.size()), i);
        }
        for (int i = 0; i < 45; i++) {
            createDocumentRequest(activeRequesters.get((i + 7) % activeRequesters.size()), hrUsers.get(i % hrUsers.size()), i);
        }
        for (int i = 0; i < 35; i++) {
            createLoanRequest(activeRequesters.get((i + 13) % activeRequesters.size()), hrUsers.get(i % hrUsers.size()), i);
        }
        for (int i = 0; i < 50; i++) {
            createAuthorizationRequest(activeRequesters.get((i + 19) % activeRequesters.size()), hrUsers.get(i % hrUsers.size()), i);
        }
    }

    private void createLeaveRequest(User requester, User hrUser, int index) {
        LeaveRequest leave = new LeaveRequest();
        leave.setUser(requester);
        leave.setLeaveType(LeaveType.values()[index % LeaveType.values().length]);
        leave.setStartDate(LocalDate.now().plusDays((index % 18) - 6L));
        leave.setEndDate(leave.getStartDate().plusDays(index % 4));
        leave.setNumberOfDays((index % 4) + 1);
        leave.setReason(reason("leave", index));
        leave.setRequestDate(LocalDateTime.now().minusDays(index % 40));
        leave.setSystemScore(55 + (index % 40));
        leave.setSystemRecommendation(index % 5 == 0 ? "REVIEW" : "APPROVE");
        leave.setDecisionReason("Seeded leave score for local testing.");

        int state = index % 5;
        if (state == 0) {
            leave.setStatus(LeaveStatus.PENDING);
            leave.setTeamLeaderDecision(ApprovalDecision.PENDING);
            leave.setHrDecision(ApprovalDecision.PENDING);
        } else if (state == 1) {
            leave.setStatus(LeaveStatus.PENDING);
            leave.setTeamLeaderDecision(ApprovalDecision.APPROVED);
            leave.setHrDecision(ApprovalDecision.PENDING);
        } else if (state == 2) {
            leave.setStatus(LeaveStatus.APPROVED);
            leave.setTeamLeaderDecision(ApprovalDecision.APPROVED);
            leave.setHrDecision(ApprovalDecision.APPROVED);
            leave.setApprovedBy(hrUser.getKeycloakId());
            leave.setApprovalDate(LocalDate.now().minusDays(index % 8));
            leave.setApprovedAt(LocalDateTime.now().minusDays(index % 8));
            leave.setVerificationToken(UUID.randomUUID().toString());
        } else if (state == 3) {
            leave.setStatus(LeaveStatus.REJECTED);
            leave.setTeamLeaderDecision(ApprovalDecision.APPROVED);
            leave.setHrDecision(ApprovalDecision.REJECTED);
            leave.setRejectedBy(hrUser.getKeycloakId());
            leave.setRejectedAt(LocalDateTime.now().minusDays(index % 6));
        } else {
            leave.setStatus(LeaveStatus.CANCELLED_BY_EMPLOYEE);
            leave.setCanceledBy(requester.getKeycloakId());
            leave.setCanceledAt(LocalDateTime.now().minusDays(index % 5));
        }

        LeaveRequest saved = leaveRequestRepository.save(leave);
        history("LEAVE", "CREATED", saved.getId(), requester.getKeycloakId(), saved.getReason(), null, "PENDING_TL");
        if (state == 1) history("LEAVE", "TL_APPROVED", saved.getId(), requester.getTeam().getTeamLeader().getKeycloakId(), "Team leader approved.", "PENDING_TL", "PENDING_HR");
        if (state == 2) history("LEAVE", "HR_APPROVED", saved.getId(), hrUser.getKeycloakId(), "Approved for seeded scenario.", "PENDING_HR", "APPROVED");
        if (state == 3) history("LEAVE", "HR_REJECTED", saved.getId(), hrUser.getKeycloakId(), "Rejected for seeded scenario.", "PENDING_HR", "REJECTED");
        if (state == 4) history("LEAVE", "EMPLOYEE_CANCELLED", saved.getId(), requester.getKeycloakId(), "Employee cancelled request.", "PENDING_TL", "CANCELLED_BY_EMPLOYEE");
        notifyUser(requester, "Leave request " + saved.getStatus().name().toLowerCase(Locale.ROOT), "LEAVE_STATUS", "LEAVE", saved.getId(), "/employee/leaves?leaveId=" + saved.getId());
    }

    private void createDocumentRequest(User requester, User hrUser, int index) {
        DocumentRequest request = new DocumentRequest();
        request.setUser(requester);
        request.setDocumentType(DocumentType.values()[index % DocumentType.values().length]);
        request.setNotes(reason("document", index));
        request.setRequestedAt(LocalDateTime.now().minusDays(index % 30));
        request.setFulfillmentMode(index % 5 == 1 ? DocumentFulfillmentMode.UPLOADED : DocumentFulfillmentMode.GENERATED);

        int state = index % 5;
        if (state == 0) {
            request.setStatus(RequestStatus.PENDING);
        } else if (state == 1) {
            request.setStatus(RequestStatus.APPROVED);
            request.setApprovedBy(hrUser.getKeycloakId());
            request.setProcessedAt(LocalDateTime.now().minusDays(2));
            request.setHrNote("Approved, waiting for HR file upload.");
        } else if (state == 2) {
            request.setStatus(RequestStatus.APPROVED);
            request.setApprovedBy(hrUser.getKeycloakId());
            request.setProcessedAt(LocalDateTime.now().minusDays(3));
            request.setVerificationToken(UUID.randomUUID().toString());
        } else if (state == 3) {
            request.setStatus(RequestStatus.REJECTED);
            request.setRejectedBy(hrUser.getKeycloakId());
            request.setProcessedAt(LocalDateTime.now().minusDays(4));
            request.setHrNote("Seeded rejection reason.");
        } else {
            request.setStatus(RequestStatus.CANCELLED_BY_EMPLOYEE);
            request.setCanceledBy(requester.getKeycloakId());
            request.setCanceledAt(LocalDateTime.now().minusDays(1));
            request.setProcessedAt(request.getCanceledAt());
        }

        DocumentRequest saved = documentRequestRepository.save(request);
        history("DOCUMENT", "CREATED", saved.getId(), requester.getKeycloakId(), saved.getNotes(), null, "PENDING");
        if (state == 2) history("DOCUMENT", "HR_APPROVED", saved.getId(), hrUser.getKeycloakId(), "Generated document ready.", "PENDING", "APPROVED");
        if (state == 3) history("DOCUMENT", "HR_REJECTED", saved.getId(), hrUser.getKeycloakId(), saved.getHrNote(), "PENDING", "REJECTED");
        if (state == 4) history("DOCUMENT", "EMPLOYEE_CANCELLED", saved.getId(), requester.getKeycloakId(), "Employee cancelled request.", "PENDING", "CANCELLED_BY_EMPLOYEE");
        notifyUser(requester, "Document request " + saved.getStatus().name().toLowerCase(Locale.ROOT), "DOCUMENT_STATUS", "DOCUMENT", saved.getId(), "/employee/requests/documents?requestId=" + saved.getId());
    }

    private void createLoanRequest(User requester, User hrUser, int index) {
        LoanRequest loan = new LoanRequest();
        loan.setUser(requester);
        loan.setLoanType(LoanType.values()[index % LoanType.values().length]);
        loan.setAmount(BigDecimal.valueOf(1200 + (index % 15) * 350L).setScale(2, RoundingMode.HALF_UP));
        loan.setRepaymentMonths(6 + (index % 5) * 3);
        loan.setReason(reason("loan", index));
        loan.setRequestedAt(LocalDateTime.now().minusDays(index % 35));
        loan.setMonthlyInstallment(loan.getAmount().divide(BigDecimal.valueOf(loan.getRepaymentMonths()), 2, RoundingMode.HALF_UP));
        loan.setRiskScore(30 + (index % 65));
        loan.setSystemRecommendation(index % 6 == 5 ? "REJECT" : index % 4 == 0 ? "REVIEW" : "APPROVE");
        loan.setDecisionReason("Seeded loan risk evaluation.");
        loan.setMeetingRequired(index % 6 == 1);

        int state = index % 7;
        if (state == 0) {
            loan.setStatus(RequestStatus.PENDING);
        } else if (state == 1) {
            loan.setStatus(RequestStatus.PENDING);
            loan.setMeetingAt(LocalDateTime.now().plusDays(3 + index % 8).withHour(10).withMinute(0).withSecond(0).withNano(0));
            loan.setMeetingNote("Seeded review meeting.");
            loan.setMeetingScheduledBy(hrUser.getKeycloakId());
            loan.setMeetingScheduledAt(LocalDateTime.now().minusDays(1));
        } else if (state == 2 || state == 3) {
            loan.setStatus(RequestStatus.APPROVED);
            loan.setApprovedBy(hrUser.getKeycloakId());
            loan.setApprovedAt(LocalDateTime.now().minusDays(2));
            loan.setProcessedAt(loan.getApprovedAt());
            loan.setApprovedAmount(loan.getAmount().subtract(BigDecimal.valueOf(state == 2 ? 0 : 150)).setScale(2, RoundingMode.HALF_UP));
            loan.setApprovedAmountJustification(state == 2 ? "Full seeded approval." : "Partial seeded approval.");
            loan.setVerificationToken(UUID.randomUUID().toString());
            if (state == 3) attachLoanFile(loan, "seed-loan-final-" + index + ".pdf");
        } else if (state == 4) {
            loan.setStatus(RequestStatus.REJECTED);
            loan.setRejectedBy(hrUser.getKeycloakId());
            loan.setRejectedAt(LocalDateTime.now().minusDays(2));
            loan.setProcessedAt(loan.getRejectedAt());
            loan.setHrDecisionReason("Seeded HR rejection.");
        } else if (state == 5) {
            loan.setStatus(RequestStatus.SYSTEM_REJECTED);
            loan.setProcessedAt(LocalDateTime.now().minusDays(1));
            loan.setHrDecisionReason("Seeded system risk rejection.");
        } else {
            loan.setStatus(RequestStatus.CANCELLED_AFTER_MEETING);
            loan.setMeetingAt(LocalDateTime.now().minusDays(2).withHour(9).withMinute(0).withSecond(0).withNano(0));
            loan.setCancellationReason("Cancelled after seeded meeting.");
            loan.setCanceledBy(requester.getKeycloakId());
            loan.setCanceledAt(LocalDateTime.now().minusDays(1));
            loan.setProcessedAt(loan.getCanceledAt());
        }

        LoanRequest saved = loanRequestRepository.save(loan);
        history("LOAN", "CREATED", saved.getId(), requester.getKeycloakId(), saved.getReason(), null, "PENDING");
        if (state == 1) history("LOAN", "MEETING_SCHEDULED", saved.getId(), hrUser.getKeycloakId(), saved.getMeetingNote(), "PENDING", "MEETING_SCHEDULED");
        if (state == 2 || state == 3) history("LOAN", "HR_APPROVED", saved.getId(), hrUser.getKeycloakId(), saved.getApprovedAmountJustification(), "PENDING", "APPROVED");
        if (state == 4) history("LOAN", "HR_REJECTED", saved.getId(), hrUser.getKeycloakId(), saved.getHrDecisionReason(), "PENDING", "REJECTED");
        if (state == 5) history("LOAN", "SYSTEM_REJECTED", saved.getId(), "SYSTEM", saved.getHrDecisionReason(), "PENDING", "SYSTEM_REJECTED");
        if (state == 6) history("LOAN", "CANCELLED_AFTER_MEETING", saved.getId(), requester.getKeycloakId(), saved.getCancellationReason(), "MEETING_SCHEDULED", "CANCELLED_AFTER_MEETING");
        notifyUser(requester, "Loan request " + saved.getStatus().name().toLowerCase(Locale.ROOT), "LOAN_STATUS", "LOAN", saved.getId(), "/employee/requests/loans?requestId=" + saved.getId());
    }

    private void attachLoanFile(LoanRequest loan, String fileName) {
        try {
            LoanRequest saved = loanRequestRepository.save(loan);
            StoredAttachment attachment = attachmentStorage.store(saved.getId(), fileName, "application/pdf", fakePdfBytes("loan " + saved.getId()));
            saved.setAttachmentFileName(attachment.getFileName());
            saved.setAttachmentContentType(attachment.getContentType());
            saved.setAttachmentStoragePath(attachment.getStoragePath());
            saved.setAttachmentSizeBytes(attachment.getSizeBytes());
            saved.setAttachmentSha256(attachment.getSha256());
            saved.setAttachmentUploadedAt(LocalDateTime.now().minusDays(1));
            saved.setAttachmentUploadedBy(saved.getApprovedBy());
        } catch (Exception e) {
            log.warn("Skipped seeded loan attachment: {}", e.getMessage());
        }
    }

    private void createAuthorizationRequest(User requester, User hrUser, int index) {
        AuthorizationRequest request = new AuthorizationRequest();
        request.setUser(requester);
        request.setAuthorizationType(index % 2 == 0 ? AuthorizationType.TIME_PERMISSION : AuthorizationType.EQUIPMENT_REQUEST);
        request.setReason(reason("authorization", index));
        request.setRequestedAt(LocalDateTime.now().minusDays(index % 25));
        if (request.getAuthorizationType() == AuthorizationType.TIME_PERMISSION) {
            request.setAbsenceDate(LocalDate.now().plusDays((index % 10) - 3L));
            request.setFromTime(LocalTime.of(9 + (index % 3), 0));
            request.setToTime(request.getFromTime().plusHours(2));
        } else {
            request.setStartDate(LocalDate.now().plusDays(index % 9));
            request.setEndDate(request.getStartDate().plusDays(5 + index % 8));
            request.setEquipmentType(index % 3 == 0 ? "Laptop" : index % 3 == 1 ? "Monitor" : "Security badge");
        }

        int state = index % 4;
        if (state == 0) {
            request.setStatus(RequestStatus.PENDING);
        } else if (state == 1) {
            request.setStatus(RequestStatus.APPROVED);
            request.setApprovedBy(hrUser.getKeycloakId());
            request.setProcessedAt(LocalDateTime.now().minusDays(1));
            request.setVerificationToken(UUID.randomUUID().toString());
        } else if (state == 2) {
            request.setStatus(RequestStatus.REJECTED);
            request.setRejectedBy(hrUser.getKeycloakId());
            request.setProcessedAt(LocalDateTime.now().minusDays(1));
            request.setHrNote("Seeded authorization rejection.");
        } else {
            request.setStatus(RequestStatus.CANCELLED_BY_EMPLOYEE);
            request.setCanceledBy(requester.getKeycloakId());
            request.setCanceledAt(LocalDateTime.now().minusHours(12));
            request.setProcessedAt(request.getCanceledAt());
        }

        AuthorizationRequest saved = authorizationRequestRepository.save(request);
        history("AUTH", "CREATED", saved.getId(), requester.getKeycloakId(), saved.getReason(), null, "PENDING");
        if (state == 1) history("AUTH", "HR_APPROVED", saved.getId(), hrUser.getKeycloakId(), "Approved authorization.", "PENDING", "APPROVED");
        if (state == 2) history("AUTH", "HR_REJECTED", saved.getId(), hrUser.getKeycloakId(), saved.getHrNote(), "PENDING", "REJECTED");
        if (state == 3) history("AUTH", "EMPLOYEE_CANCELLED", saved.getId(), requester.getKeycloakId(), "Employee cancelled authorization.", "PENDING", "CANCELLED_BY_EMPLOYEE");
        notifyUser(requester, "Authorization request " + saved.getStatus().name().toLowerCase(Locale.ROOT), "AUTH_STATUS", "AUTH", saved.getId(), "/employee/requests/authorizations?requestId=" + saved.getId());
    }

    private String reason(String type, int index) {
        List<String> reasons = List.of(
                "Quarterly planning support",
                "Customer delivery milestone",
                "Operational coverage",
                "Family logistics",
                "Professional development",
                "Health appointment",
                "Equipment refresh",
                "Compliance follow-up"
        );
        return "Seeded " + type + " request: " + reasons.get(index % reasons.size()) + ".";
    }

    private void createProjectsAndTasks(List<Team> teams) {
        List<Project> projects = new ArrayList<>();
        for (int i = 0; i < teams.size(); i++) {
            for (int j = 0; j < 2; j++) {
                Project project = new Project();
                project.setTeam(teams.get(i));
                project.setName("Seed Project " + String.format("%02d", i + 1) + "-" + (j + 1));
                project.setDescription("Seeded local project for " + teams.get(i).getName() + " " + SEED_MARKER);
                projects.add(projectRepository.save(project));
            }
        }

        List<User> assignees = userRepository.findAll().stream()
                .filter(this::isSeededUser)
                .filter(user -> user.getRole() == TypeRole.EMPLOYEE)
                .filter(user -> user.getTeam() != null)
                .sorted(Comparator.comparing(User::getUsername))
                .toList();

        int taskCount = 150;
        for (int i = 0; i < taskCount; i++) {
            Project project = projects.get(i % projects.size());
            List<User> teamAssignees = assignees.stream()
                    .filter(user -> user.getTeam() != null && Objects.equals(user.getTeam().getId(), project.getTeam().getId()))
                    .toList();
            User assignee = teamAssignees.isEmpty() ? null : teamAssignees.get(i % teamAssignees.size());
            Task task = new Task();
            task.setProject(project);
            task.setAssignee(assignee);
            task.setTitle("Seed Task " + String.format("%03d", i + 1));
            task.setDescription("Seeded task for local project planning and dashboard testing.");
            task.setStatus(TaskStatus.values()[i % TaskStatus.values().length]);
            task.setPriority(TaskPriority.values()[i % TaskPriority.values().length]);
            task.setStartDate(LocalDate.now().minusDays(i % 12));
            task.setDueDate(LocalDate.now().plusDays((i % 20) - 6L));
            Task saved = taskRepository.save(task);
            if (assignee != null && i % 3 == 0) {
                notifyUser(assignee, "New task assigned: " + saved.getTitle(), "TASK_ASSIGNED", "TASK", saved.getId(), "/employee/tasks?taskId=" + saved.getId());
            }
        }
    }

    private void history(String type, String action, Long requestId, String actorId, String comment, String fromState, String toState) {
        requestHistoryRepository.save(new RequestHistory(requestId, type, action, actorId, comment, fromState, toState));
    }

    private void notifyUser(User user, String message, String type, String referenceType, Long referenceId, String actionUrl) {
        notificationRepository.save(new Notification(user, message, type, referenceType, referenceId, actionUrl));
    }

    private byte[] fakePdfBytes(String label) {
        return ("%PDF-1.4\n% Seed dev data attachment: " + label + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private record ResetSnapshot(
            List<Long> userIds,
            List<Long> personIds,
            List<String> keycloakIds,
            List<Long> teamIds,
            List<Long> leaveIds,
            List<Long> documentIds,
            List<Long> loanIds,
            List<Long> authorizationIds,
            List<Long> taskIds,
            List<String> attachmentPaths
    ) {
    }
}
