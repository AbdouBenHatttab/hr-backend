package tn.isetbizerte.pfe.hrbackend.modules.team.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.isetbizerte.pfe.hrbackend.common.enums.TypeRole;
import tn.isetbizerte.pfe.hrbackend.common.exception.BadRequestException;
import tn.isetbizerte.pfe.hrbackend.common.exception.ResourceNotFoundException;
import tn.isetbizerte.pfe.hrbackend.modules.team.entity.Team;
import tn.isetbizerte.pfe.hrbackend.modules.team.repository.TeamRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageImpl;

@Service
public class TeamService {

    private static final Logger logger = LoggerFactory.getLogger(TeamService.class);

    private final TeamRepository teamRepository;
    private final UserRepository userRepository;

    public TeamService(TeamRepository teamRepository, UserRepository userRepository) {
        this.teamRepository = teamRepository;
        this.userRepository = userRepository;
    }

    // ─────────────────────────────────────────────────────────────
    // HR OPERATIONS
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> createTeam(String name, String description, Long teamLeaderId) {
        if (teamRepository.existsByName(name))
            throw new BadRequestException("A team with the name '" + name + "' already exists.");

        User leader = userRepository.findById(teamLeaderId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + teamLeaderId));

        if (leader.getRole() != TypeRole.TEAM_LEADER)
            throw new BadRequestException("User is not a TEAM_LEADER. Assign the TEAM_LEADER role first.");

        if (teamRepository.findByTeamLeader(leader).isPresent())
            throw new BadRequestException("This Team Leader is already assigned to another team.");

        Team team = new Team();
        team.setName(name);
        team.setDescription(description);
        team.setTeamLeader(leader);
        teamRepository.save(team);
        logger.info("Team '{}' created with leader '{}'", name, leader.getUsername());

        // Build response directly from known data — no lazy loading
        return buildTeamResponse(team.getId(), team.getName(), team.getDescription(),
                team.getCreatedAt(), leader, List.of());
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
                        base.getCreatedAt(), leader, members);
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
                        base.getCreatedAt(), leader, members);
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
                withLeader.getCreatedAt(), leader, members);
    }

    // ─────────────────────────────────────────────────────────────
    // TEAM LEADER OPERATIONS
    // ─────────────────────────────────────────────────────────────

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
                    if (u.getPerson() != null) {
                        m.put("fullName", u.getPerson().getFirstName() + " " + u.getPerson().getLastName());
                        m.put("email", u.getPerson().getEmail());
                    }
                    return m;
                })
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────
    // INTERNAL HELPERS
    // ─────────────────────────────────────────────────────────────

    private Team getTeamByLeaderKeycloakId(String keycloakId) {
        return teamRepository.findByTeamLeaderKeycloakId(keycloakId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No team found for this Team Leader. Ask HR to assign you a team."));
    }

    /**
     * Builds the team response map from already-loaded, non-lazy data.
     * Never touches lazy associations — all data is passed explicitly.
     */
    private Map<String, Object> buildTeamResponse(
            Long id, String name, String description,
            java.time.LocalDateTime createdAt,
            User leader, List<User> members) {

        Map<String, Object> response = new HashMap<>();
        response.put("id",          id);
        response.put("name",        name);
        response.put("description", description);
        response.put("createdAt",   createdAt);

        if (leader != null) {
            Map<String, Object> leaderInfo = new HashMap<>();
            leaderInfo.put("id",       leader.getId());
            leaderInfo.put("username", leader.getUsername());
            if (leader.getPerson() != null) {
                leaderInfo.put("fullName",
                    leader.getPerson().getFirstName() + " " + leader.getPerson().getLastName());
                leaderInfo.put("email", leader.getPerson().getEmail());
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
                    }
                    return m;
                })
                .collect(Collectors.toList());

        response.put("members",     memberList);
        response.put("memberCount", memberList.size());
        return response;
    }

    // Keep old mapTeamToResponse as unused safety fallback — removed to avoid confusion
}
