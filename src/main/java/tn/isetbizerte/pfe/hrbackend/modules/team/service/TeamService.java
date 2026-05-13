package tn.isetbizerte.pfe.hrbackend.modules.team.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.isetbizerte.pfe.hrbackend.common.enums.TypeRole;
import tn.isetbizerte.pfe.hrbackend.common.exception.BadRequestException;
import tn.isetbizerte.pfe.hrbackend.common.exception.ResourceNotFoundException;
import tn.isetbizerte.pfe.hrbackend.infrastructure.email.HREmailService;
import tn.isetbizerte.pfe.hrbackend.infrastructure.kafka.producer.NotificationEventProducer;
import tn.isetbizerte.pfe.hrbackend.modules.department.entity.Department;
import tn.isetbizerte.pfe.hrbackend.modules.department.repository.DepartmentRepository;
import tn.isetbizerte.pfe.hrbackend.modules.employee.repository.LeaveRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.team.entity.Team;
import tn.isetbizerte.pfe.hrbackend.modules.team.dto.UpdateTeamRequest;
import tn.isetbizerte.pfe.hrbackend.modules.team.repository.TeamRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageImpl;

@Service
public class TeamService {

    private static final Logger logger = LoggerFactory.getLogger(TeamService.class);

    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final DepartmentRepository departmentRepository;
    private final TeamNotificationService teamNotificationService;

    @Autowired
    public TeamService(TeamRepository teamRepository,
                       UserRepository userRepository,
                       LeaveRequestRepository leaveRequestRepository,
                       DepartmentRepository departmentRepository,
                       TeamNotificationService teamNotificationService) {
        this.teamRepository = teamRepository;
        this.userRepository = userRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.departmentRepository = departmentRepository;
        this.teamNotificationService = teamNotificationService;
    }

    public TeamService(TeamRepository teamRepository,
                       UserRepository userRepository,
                       LeaveRequestRepository leaveRequestRepository,
                       NotificationEventProducer notificationEventProducer,
                       HREmailService emailService) {
        this(
                teamRepository,
                userRepository,
                leaveRequestRepository,
                null,
                new TeamNotificationService(notificationEventProducer, emailService)
        );
    }

    // HR OPERATIONS

    @Transactional
    public Map<String, Object> createTeam(String name, String description, Long teamLeaderId) {
        return createTeam(name, description, teamLeaderId, null);
    }

    @Transactional
    public Map<String, Object> createTeam(String name, String description, Long teamLeaderId, Long departmentId) {
        String normalizedName = name != null ? name.trim() : "";
        String normalizedDescription = description != null ? description.trim() : null;

        if (normalizedName.isBlank())
            throw new BadRequestException("Team name is required");

        if (teamRepository.existsByNameIgnoreCase(normalizedName))
            throw new BadRequestException("A team with this name already exists.");

        User leader = null;
        if (teamLeaderId != null) {
            leader = userRepository.findById(teamLeaderId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + teamLeaderId));

            if (leader.getRole() != TypeRole.TEAM_LEADER)
                throw new BadRequestException("User is not a TEAM_LEADER. Assign the TEAM_LEADER role first.");

            if (teamRepository.findByTeamLeader(leader).isPresent())
                throw new BadRequestException("This Team Leader is already assigned to another team.");
        }

        Department department = null;
        if (departmentId != null) {
            if (departmentRepository == null) {
                throw new IllegalStateException("Department repository is not configured.");
            }
            department = departmentRepository.findById(departmentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Department not found with ID: " + departmentId));
            if (!Boolean.TRUE.equals(department.getActive())) {
                throw new BadRequestException("Department '" + department.getName() + "' is archived and cannot be assigned.");
            }
        }

        Team team = new Team();
        team.setName(normalizedName);
        team.setDescription(normalizedDescription);
        team.setTeamLeader(leader);
        team.setDepartment(department);
        teamRepository.save(team);
        logger.info("Team '{}' created with leader '{}'", normalizedName, leader != null ? leader.getUsername() : "none");

        // Build response directly from known data - no lazy loading
        return buildTeamResponse(team.getId(), team.getName(), team.getDescription(),
                team.getCreatedAt(), team.getDepartment(), leader, List.of());
    }

    @Transactional
    public Map<String, Object> updateTeamMetadata(Long teamId, UpdateTeamRequest request) {
        Team team = teamRepository.findByIdWithDetails(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));
        Team withMembers = teamRepository.findByIdWithMembers(teamId).orElse(team);
        List<User> members = withMembers.getMembers() != null ? withMembers.getMembers() : List.of();

        String normalizedName = request.getName() != null ? request.getName().trim() : "";
        if (normalizedName.isBlank())
            throw new BadRequestException("Team name is required");

        if (teamRepository.existsByNameIgnoreCaseAndIdNot(normalizedName, teamId))
            throw new BadRequestException("A team with this name already exists.");

        Long departmentId = request.getDepartmentId();
        if (departmentId == null)
            throw new BadRequestException("Department is required");

        if (departmentRepository == null) {
            throw new IllegalStateException("Department repository is not configured.");
        }

        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found with ID: " + departmentId));
        if (!Boolean.TRUE.equals(department.getActive())) {
            throw new BadRequestException("Department '" + department.getName() + "' is archived and cannot be assigned.");
        }

        String normalizedDescription = request.getDescription() != null ? request.getDescription().trim() : null;
        if (normalizedDescription != null && normalizedDescription.isBlank()) {
            normalizedDescription = null;
        }

        team.setName(normalizedName);
        team.setDescription(normalizedDescription);
        team.setDepartment(department);
        touchTeam(team);

        return buildTeamResponse(team.getId(), team.getName(), team.getDescription(),
                team.getCreatedAt(), team.getDepartment(), team.getTeamLeader(), members);
    }

    @Transactional
    public Map<String, Object> changeTeamLeader(Long teamId, Long newLeaderId) {
        Team team = teamRepository.findByIdWithDetails(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));
        Team withMembers = teamRepository.findByIdWithMembers(teamId).orElse(team);
        List<User> members = withMembers.getMembers() != null ? withMembers.getMembers() : List.of();

        User oldLeader = team.getTeamLeader();
        User newLeader = userRepository.findById(newLeaderId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + newLeaderId));

        if (oldLeader != null && Objects.equals(oldLeader.getId(), newLeader.getId())) {
            return buildTeamResponse(team.getId(), team.getName(), team.getDescription(),
                    team.getCreatedAt(), team.getDepartment(), oldLeader, members);
        }

        if (!Boolean.TRUE.equals(newLeader.getActive()))
            throw new BadRequestException("New Team Leader must be active.");

        if (newLeader.getRole() != TypeRole.TEAM_LEADER)
            throw new BadRequestException("User is not a TEAM_LEADER. Assign the TEAM_LEADER role first.");

        teamRepository.findByTeamLeader(newLeader)
                .filter(existing -> !Objects.equals(existing.getId(), teamId))
                .ifPresent(existing -> {
                    throw new BadRequestException("This Team Leader is already assigned to another team.");
                });

        if (newLeader.getTeam() != null)
            throw new BadRequestException("This Team Leader is already a regular member of another team.");

        team.setTeamLeader(newLeader);
        touchTeam(team);
        logger.info("Team '{}' leader changed from '{}' to '{}'",
                team.getName(),
                oldLeader != null ? oldLeader.getUsername() : "none",
                newLeader.getUsername());

        teamNotificationService.notifyTeamLeaderAssigned(newLeader, team.getId(), team.getName());

        if (oldLeader != null && !Objects.equals(oldLeader.getId(), newLeader.getId())) {
            teamNotificationService.notifyTeamLeaderRemoved(oldLeader, team.getId(), team.getName());
        }

        return buildTeamResponse(team.getId(), team.getName(), team.getDescription(),
                team.getCreatedAt(), team.getDepartment(), newLeader, members);
    }

    @Transactional
    public Map<String, Object> assignEmployeeToTeam(Long userId, Long teamId) {
        User employee = loadActiveEmployee(userId);
        if (employee.getTeam() != null)
            throw new BadRequestException("This employee is already assigned to a team.");

        Team targetTeam = loadTeamForMembership(teamId);

        employee.setTeam(targetTeam);
        userRepository.save(employee);
        touchTeam(targetTeam);

        teamNotificationService.notifyTeamAssigned(employee, targetTeam.getId(), targetTeam.getName());

        return buildMembershipResponse("Employee assigned to team successfully", employee, targetTeam, null);
    }

    @Transactional
    public Map<String, Object> moveEmployeeToTeam(Long userId, Long targetTeamId) {
        User employee = loadActiveEmployee(userId);
        Team currentTeam = employee.getTeam();
        if (currentTeam == null)
            throw new BadRequestException("This employee is not assigned to a team.");

        Team targetTeam = loadTeamForMembership(targetTeamId);
        if (currentTeam.getId().equals(targetTeam.getId()))
            throw new BadRequestException("Employee is already assigned to this team.");

        validateNoPendingTeamLeaderLeave(employee.getId());

        employee.setTeam(targetTeam);
        userRepository.save(employee);
        touchTeam(currentTeam);
        touchTeam(targetTeam);

        teamNotificationService.notifyTeamChanged(employee, targetTeam.getId(), currentTeam.getName(), targetTeam.getName());

        return buildMembershipResponse("Employee moved to team successfully", employee, targetTeam, currentTeam);
    }

    @Transactional
    public Map<String, Object> removeEmployeeFromTeam(Long userId) {
        User employee = loadActiveEmployee(userId);
        Team currentTeam = employee.getTeam();
        if (currentTeam == null)
            throw new BadRequestException("This employee is not assigned to a team.");

        validateNoPendingTeamLeaderLeave(employee.getId());

        employee.setTeam(null);
        userRepository.save(employee);
        touchTeam(currentTeam);

        teamNotificationService.notifyTeamRemoved(employee, currentTeam.getId(), currentTeam.getName());

        return buildMembershipResponse("Employee removed from team successfully", employee, null, currentTeam);
    }

    /** HR views all teams. Uses two separate queries per team to avoid MultipleBagFetchException. */
    public List<Map<String, Object>> getAllTeams() {
        // Step 1: get all team IDs
        List<Long> ids = teamRepository.findAll().stream()
                .map(Team::getId)
                .collect(Collectors.toList());

        return ids.stream().map(id -> {
            try {
                // Step 2: load leader+person for this team
                Team withLeader  = teamRepository.findByIdWithDetails(id).orElse(null);
                // Step 3: load members+person for this team
                Team withMembers = teamRepository.findByIdWithMembers(id).orElse(null);

                if (withLeader == null && withMembers == null) return null;

                Team base = withLeader != null ? withLeader : withMembers;
                User leader = withLeader != null ? withLeader.getTeamLeader() : null;
                List<User> members = withMembers != null && withMembers.getMembers() != null
                        ? withMembers.getMembers()
                        : List.of();

                return buildTeamResponse(base.getId(), base.getName(), base.getDescription(),
                        base.getCreatedAt(), base.getDepartment(), leader, members);
            } catch (Exception e) {
                logger.error("Error loading team {}: {}", id, e.getMessage());
                return null;
            }
        }).filter(t -> t != null).collect(Collectors.toList());
    }

    /** HR views all teams with pagination. */
    public Page<Map<String, Object>> getAllTeams(Pageable pageable) {
        Page<Team> page = teamRepository.findAll(pageable);
        List<Long> ids = page.getContent().stream()
                .map(Team::getId)
                .collect(Collectors.toList());

        List<Map<String, Object>> content = ids.stream().map(id -> {
            try {
                Team withLeader  = teamRepository.findByIdWithDetails(id).orElse(null);
                Team withMembers = teamRepository.findByIdWithMembers(id).orElse(null);

                if (withLeader == null && withMembers == null) return null;

                Team base = withLeader != null ? withLeader : withMembers;
                User leader = withLeader != null ? withLeader.getTeamLeader() : null;
                List<User> members = withMembers != null && withMembers.getMembers() != null
                        ? withMembers.getMembers()
                        : List.of();

                return buildTeamResponse(base.getId(), base.getName(), base.getDescription(),
                        base.getCreatedAt(), base.getDepartment(), leader, members);
            } catch (Exception e) {
                logger.error("Error loading team {}: {}", id, e.getMessage());
                return null;
            }
        }).filter(t -> t != null).collect(Collectors.toList());

        return new PageImpl<>(content, pageable, page.getTotalElements());
    }

    /** HR views a single team by ID. */
    public Map<String, Object> getTeamById(Long teamId) {
        Team withLeader  = teamRepository.findByIdWithDetails(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));
        Team withMembers = teamRepository.findByIdWithMembers(teamId).orElse(withLeader);

        User leader = withLeader.getTeamLeader();
        List<User> members = withMembers.getMembers() != null ? withMembers.getMembers() : List.of();

        return buildTeamResponse(withLeader.getId(), withLeader.getName(), withLeader.getDescription(),
                withLeader.getCreatedAt(), withLeader.getDepartment(), leader, members);
    }

    // TEAM LEADER OPERATIONS

    @Transactional
    public Map<String, Object> addMemberToTeam(String leaderKeycloakId, Long employeeId) {
        Team team = getTeamByLeaderKeycloakId(leaderKeycloakId);

        User employee = userRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + employeeId));

        if (employee.getRole() != TypeRole.EMPLOYEE)
            throw new BadRequestException("Only EMPLOYEE users can be added to a team.");

        if (employee.getTeam() != null)
            throw new BadRequestException("Employee is already assigned to team: " + employee.getTeam().getName());

        employee.setTeam(team);
        userRepository.save(employee);
        team.setUpdatedAt(LocalDateTime.now());
        teamRepository.save(team);

        logger.info("Employee '{}' added to team '{}'", employee.getUsername(), team.getName());

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Employee added to team successfully");
        response.put("teamId", team.getId());
        response.put("teamName", team.getName());
        response.put("employeeId", employee.getId());
        response.put("employeeUsername", employee.getUsername());
        return response;
    }

    @Transactional
    public Map<String, Object> removeMemberFromTeam(String leaderKeycloakId, Long employeeId) {
        Team team = getTeamByLeaderKeycloakId(leaderKeycloakId);

        User employee = userRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + employeeId));

        if (employee.getTeam() == null || !employee.getTeam().getId().equals(team.getId()))
            throw new BadRequestException("This employee does not belong to your team.");

        employee.setTeam(null);
        userRepository.save(employee);
        team.setUpdatedAt(LocalDateTime.now());
        teamRepository.save(team);

        logger.info("Employee '{}' removed from team '{}'", employee.getUsername(), team.getName());

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Employee removed from team successfully");
        response.put("teamId", team.getId());
        response.put("employeeId", employee.getId());
        return response;
    }

    public Map<String, Object> getMyTeam(String leaderKeycloakId) {
        Team teamRef = getTeamByLeaderKeycloakId(leaderKeycloakId);
        return getTeamById(teamRef.getId());
    }

    public List<Map<String, Object>> getAvailableEmployees() {
        return userRepository.findAll().stream()
                .filter(u -> u.getRole() == TypeRole.EMPLOYEE
                        && Boolean.TRUE.equals(u.getActive())
                        && u.getTeam() == null)
                .map(u -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", u.getId());
                    m.put("username", u.getUsername());
                    m.put("role", u.getRole().name());
                    m.put("active", u.getActive());
                    m.put("registrationDate", u.getRegistrationDate());
                    if (u.getPerson() != null) {
                        m.put("fullName", u.getPerson().getFirstName() + " " + u.getPerson().getLastName());
                        m.put("email", u.getPerson().getEmail());

                        Map<String, Object> personalInfo = new HashMap<>();
                        personalInfo.put("firstName",  u.getPerson().getFirstName());
                        personalInfo.put("lastName",   u.getPerson().getLastName());
                        personalInfo.put("email",      u.getPerson().getEmail());
                        personalInfo.put("phone",      u.getPerson().getPhone() != null ? u.getPerson().getPhone() : "");
                        personalInfo.put("address",    u.getPerson().getAddress() != null ? u.getPerson().getAddress() : "");
                        personalInfo.put("birthDate",  u.getPerson().getBirthDate() != null ? u.getPerson().getBirthDate().toString() : "");
                        personalInfo.put("maritalStatus", u.getPerson().getMaritalStatus() != null ? u.getPerson().getMaritalStatus() : "");
                        personalInfo.put("numberOfChildren", u.getPerson().getNumberOfChildren());
                        personalInfo.put("departmentId", u.getPerson().getDepartmentId());
                        personalInfo.put("department", u.getPerson().getDepartment() != null ? u.getPerson().getDepartment() : "");
                        personalInfo.put("hireDate",   u.getPerson().getHireDate() != null ? u.getPerson().getHireDate().toString() : "");
                        personalInfo.put("avatarPhoto", u.getPerson().getAvatarPhoto() != null ? u.getPerson().getAvatarPhoto() : "");
                        personalInfo.put("avatarColor", u.getPerson().getAvatarColor() != null ? u.getPerson().getAvatarColor() : "");
                        m.put("personalInfo", personalInfo);
                    }
                    return m;
                })
                .collect(Collectors.toList());
    }

    /**
     * Read-only employee profile for Team Leader.
     * Allowed when employee is unassigned OR belongs to the Team Leader's team.
     */
    public Map<String, Object> getEmployeeProfileForLeader(String leaderKeycloakId, Long employeeId) {
        Team team = getTeamByLeaderKeycloakId(leaderKeycloakId);

        User employee = userRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + employeeId));

        if (employee.getRole() != TypeRole.EMPLOYEE) {
            throw new BadRequestException("This user is not an EMPLOYEE.");
        }

        if (employee.getTeam() != null && !Objects.equals(employee.getTeam().getId(), team.getId())) {
            throw new BadRequestException("You are not allowed to view this employee profile.");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("id", employee.getId());
        response.put("username", employee.getUsername());
        response.put("role", employee.getRole().name());
        response.put("active", employee.getActive());
        response.put("registrationDate", employee.getRegistrationDate());

        if (employee.getTeam() != null) {
            response.put("teamId", employee.getTeam().getId());
            response.put("teamName", employee.getTeam().getName());
        } else {
            response.put("teamId", null);
            response.put("teamName", null);
        }

        if (employee.getPerson() != null) {
            Map<String, Object> personalInfo = new HashMap<>();
            personalInfo.put("firstName",        employee.getPerson().getFirstName());
            personalInfo.put("lastName",         employee.getPerson().getLastName());
            personalInfo.put("email",            employee.getPerson().getEmail());
            personalInfo.put("phone",            employee.getPerson().getPhone() != null ? employee.getPerson().getPhone() : "");
            personalInfo.put("address",          employee.getPerson().getAddress() != null ? employee.getPerson().getAddress() : "");
            personalInfo.put("birthDate",        employee.getPerson().getBirthDate() != null ? employee.getPerson().getBirthDate().toString() : "");
            personalInfo.put("maritalStatus",    employee.getPerson().getMaritalStatus() != null ? employee.getPerson().getMaritalStatus() : "");
            personalInfo.put("numberOfChildren", employee.getPerson().getNumberOfChildren());
            personalInfo.put("avatarPhoto",      employee.getPerson().getAvatarPhoto() != null ? employee.getPerson().getAvatarPhoto() : "");
            personalInfo.put("avatarColor",      employee.getPerson().getAvatarColor() != null ? employee.getPerson().getAvatarColor() : "");
            personalInfo.put("departmentId",     employee.getPerson().getDepartmentId());
            personalInfo.put("department",       employee.getPerson().getDepartment() != null ? employee.getPerson().getDepartment() : "");
            personalInfo.put("hireDate",         employee.getPerson().getHireDate() != null ? employee.getPerson().getHireDate().toString() : "");
            response.put("personalInfo", personalInfo);
        }

        return response;
    }

    // INTERNAL HELPERS

    private Team getTeamByLeaderKeycloakId(String keycloakId) {
        return teamRepository.findByTeamLeaderKeycloakId(keycloakId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No team found for this Team Leader. Ask HR to assign you a team."));
    }

    private User loadActiveEmployee(Long userId) {
        User employee = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        if (employee.getRole() != TypeRole.EMPLOYEE || !Boolean.TRUE.equals(employee.getActive()))
            throw new BadRequestException("Only active employees can be assigned to a team.");

        return employee;
    }

    private Team loadTeamForMembership(Long teamId) {
        Team team = teamRepository.findByIdWithDetails(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));

        if (team.getTeamLeader() == null)
            throw new BadRequestException("Target team must have a Team Leader before assigning members.");

        return team;
    }

    private void validateNoPendingTeamLeaderLeave(Long userId) {
        if (leaveRequestRepository.existsPendingTeamLeaderApprovalByUserId(userId)) {
            throw new BadRequestException(
                    "Employee has a leave request pending Team Leader approval. Resolve it before changing team assignment.");
        }
    }

    private void touchTeam(Team team) {
        if (team == null) return;
        team.setUpdatedAt(LocalDateTime.now());
        teamRepository.save(team);
    }

    private Map<String, Object> buildMembershipResponse(String message, User employee, Team currentTeam, Team previousTeam) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("employeeId", employee.getId());
        response.put("employeeUsername", employee.getUsername());
        response.put("teamId", currentTeam != null ? currentTeam.getId() : null);
        response.put("teamName", currentTeam != null ? currentTeam.getName() : null);
        response.put("previousTeamId", previousTeam != null ? previousTeam.getId() : null);
        response.put("previousTeamName", previousTeam != null ? previousTeam.getName() : null);
        return response;
    }

    /**
     * Builds the team response map from already-loaded, non-lazy data.
     * Never touches lazy associations - all data is passed explicitly.
     */
    private Map<String, Object> buildTeamResponse(
            Long id, String name, String description,
            java.time.LocalDateTime createdAt,
            Department department,
            User leader, List<User> members) {

        Map<String, Object> response = new HashMap<>();
        response.put("id",          id);
        response.put("name",        name);
        response.put("description", description);
        response.put("createdAt",   createdAt);
        response.put("departmentId", department != null ? department.getId() : null);
        response.put("departmentName", department != null ? department.getName() : null);

        if (leader != null) {
            Map<String, Object> leaderInfo = new HashMap<>();
            leaderInfo.put("id",       leader.getId());
            leaderInfo.put("username", leader.getUsername());
            if (leader.getPerson() != null) {
                leaderInfo.put("fullName",
                    leader.getPerson().getFirstName() + " " + leader.getPerson().getLastName());
                leaderInfo.put("email", leader.getPerson().getEmail());
                leaderInfo.put("avatarPhoto", leader.getPerson().getAvatarPhoto() != null ? leader.getPerson().getAvatarPhoto() : "");
                leaderInfo.put("avatarColor", leader.getPerson().getAvatarColor() != null ? leader.getPerson().getAvatarColor() : "");
            }
            response.put("teamLeader", leaderInfo);
        }

        List<Map<String, Object>> memberList = members.stream()
                .map((User member) -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id",       member.getId());
                    m.put("username", member.getUsername());
                    if (member.getPerson() != null) {
                        m.put("fullName",
                            member.getPerson().getFirstName() + " " + member.getPerson().getLastName());
                        m.put("email", member.getPerson().getEmail());

                        Map<String, Object> personalInfo = new HashMap<>();
                        personalInfo.put("firstName",   member.getPerson().getFirstName());
                        personalInfo.put("lastName",    member.getPerson().getLastName());
                        personalInfo.put("email",       member.getPerson().getEmail());
                        personalInfo.put("departmentId", member.getPerson().getDepartmentId());
                        personalInfo.put("department",  member.getPerson().getDepartment() != null ? member.getPerson().getDepartment() : "");
                        personalInfo.put("avatarPhoto", member.getPerson().getAvatarPhoto() != null ? member.getPerson().getAvatarPhoto() : "");
                        personalInfo.put("avatarColor", member.getPerson().getAvatarColor() != null ? member.getPerson().getAvatarColor() : "");
                        m.put("personalInfo", personalInfo);
                    }
                    return m;
                })
                .collect(Collectors.toList());

        int employeeMemberCount = memberList.size();
        int teamLeaderCount = leader != null ? 1 : 0;
        response.put("members",             memberList);
        response.put("memberCount",         employeeMemberCount);
        response.put("employeeMemberCount", employeeMemberCount);
        response.put("teamLeaderCount",     teamLeaderCount);
        response.put("totalPeopleCount",    employeeMemberCount + teamLeaderCount);
        return response;
    }

    // Keep old mapTeamToResponse as unused safety fallback - removed to avoid confusion
}
