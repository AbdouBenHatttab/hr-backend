package tn.isetbizerte.pfe.hrbackend.modules.employee.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tn.isetbizerte.pfe.hrbackend.common.enums.TaskPriority;
import tn.isetbizerte.pfe.hrbackend.modules.employee.entity.LeaveRequest;
import tn.isetbizerte.pfe.hrbackend.modules.employee.repository.LeaveRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.task.entity.Task;
import tn.isetbizerte.pfe.hrbackend.modules.task.repository.TaskRepository;
import tn.isetbizerte.pfe.hrbackend.modules.team.entity.Team;
import tn.isetbizerte.pfe.hrbackend.modules.team.repository.TeamRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.UserRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Leave Scoring Engine
 * ─────────────────────────────────────────────────────────────────
 * Evaluates a leave request on 4 dimensions and produces:
 *   - system_score          (0–100)
 *   - system_recommendation (APPROVE / REVIEW / REJECT)
 *   - decision_reason       (human-readable explanation)
 *
 * Designed to be null-safe: works even when the employee is not
 * assigned to a team yet, or has no prior leave history.
 */
@Service
public class LeaveScoreEngine {

    private static final Logger log = LoggerFactory.getLogger(LeaveScoreEngine.class);

    private final LeaveRequestRepository leaveRepo;
    private final TaskRepository         taskRepo;
    private final TeamRepository         teamRepo;
    private final UserRepository         userRepository;

    public LeaveScoreEngine(LeaveRequestRepository leaveRepo,
                            TaskRepository taskRepo,
                            TeamRepository teamRepo,
                            UserRepository userRepository) {
        this.leaveRepo      = leaveRepo;
        this.taskRepo       = taskRepo;
        this.teamRepo       = teamRepo;
        this.userRepository = userRepository;
    }

    public void evaluate(LeaveRequest leave) {
        User      employee = leave.getUser();
        LocalDate start    = leave.getStartDate();
        LocalDate end      = leave.getEndDate();

        if (employee == null || start == null || end == null) {
            leave.setSystemScore(50);
            leave.setSystemRecommendation("REVIEW");
            leave.setDecisionReason("Incomplete data — manual review required.");
            return;
        }

        int          score   = 100;
        List<String> reasons = new ArrayList<>();

        // Reload team fresh from DB — use findByIdWithDetails to avoid lazy loading
        Team team = null;
        try {
            if (employee.getTeam() != null && employee.getTeam().getId() != null) {
                team = teamRepo.findByIdWithDetails(employee.getTeam().getId()).orElse(null);
            }
        } catch (Exception e) {
            log.warn("Could not load team for scoring: {}", e.getMessage());
        }

        // ── A. Team availability ────────────────────────────────────────────
        int teamOverloadPenalty = 0;
        if (team != null) {
            try {
                // Count members by querying users table directly — no lazy loading
                long memberCount = userRepository.countByTeamId(team.getId());
                int  teamSize    = (int) memberCount + 1; // +1 for TL

                if (teamSize > 1) {
                    long   onLeave = leaveRepo.countOverlappingByTeam(team.getId(), start, end);
                    double pct     = (double) onLeave / teamSize * 100.0;
                    double maxPct  = team.getMaxLeavePercentage(); // default 40

                    if (pct >= maxPct) {
                        teamOverloadPenalty = 40;
                        reasons.add(String.format(
                            "Team overloaded: %.0f%% on leave (max %d%%). ", pct, (int) maxPct));
                    } else if (pct >= maxPct * 0.75) {
                        teamOverloadPenalty = 20;
                        reasons.add(String.format(
                            "Team nearing capacity: %.0f%% on leave. ", pct));
                    } else if (pct >= maxPct * 0.5) {
                        teamOverloadPenalty = 10;
                        reasons.add(String.format("%.0f%% of team already on leave. ", pct));
                    }
                }
            } catch (Exception e) {
                log.warn("Team availability check failed: {}", e.getMessage());
            }
        } else {
            reasons.add("Not yet assigned to a team — team check skipped. ");
        }
        score -= teamOverloadPenalty;

        // ── B. Fairness — compare vs team average ───────────────────────────
        int fairnessPenalty = 0;
        if (employee.getId() != null) {
            try {
                LocalDate since12Months   = LocalDate.now().minusMonths(12);
                int       empLeaveDays    = leaveRepo.sumLeaveDaysSince(employee.getId(), since12Months);
                double    teamAvg         = (team != null)
                    ? leaveRepo.avgLeaveDaysPerTeamMember(team.getId(), since12Months)
                    : (double) empLeaveDays;

                if (teamAvg > 0) {
                    double ratio = empLeaveDays / teamAvg;
                    if (ratio > 2.0) {
                        fairnessPenalty = 30;
                        reasons.add(String.format(
                            "Fairness: took %.0f days vs team avg %.0f (2× above). ",
                            (double) empLeaveDays, teamAvg));
                    } else if (ratio > 1.5) {
                        fairnessPenalty = 15;
                        reasons.add(String.format(
                            "Fairness: took %.0f days vs team avg %.0f (50%% above). ",
                            (double) empLeaveDays, teamAvg));
                    } else if (ratio > 1.2) {
                        fairnessPenalty = 5;
                        reasons.add(String.format(
                            "Slightly above team average (%.0f vs %.0f days). ",
                            (double) empLeaveDays, teamAvg));
                    }
                }
            } catch (Exception e) {
                log.warn("Fairness check failed: {}", e.getMessage());
            }
        }
        score -= fairnessPenalty;

        // ── C. Task impact ──────────────────────────────────────────────────
        int taskPenalty = 0;
        if (employee.getId() != null) {
            try {
                List<Task> activeTasks = taskRepo.findActiveTasksOverlapping(
                        employee.getId(), start, end);
                long high   = activeTasks.stream()
                        .filter(t -> t.getPriority() == TaskPriority.HIGH).count();
                long medium = activeTasks.stream()
                        .filter(t -> t.getPriority() == TaskPriority.MEDIUM).count();

                if (high > 0) {
                    taskPenalty = Math.min(25, (int)(high * 12));
                    reasons.add(String.format(
                        "%d HIGH-priority task(s) active during leave. ", high));
                } else if (medium > 0) {
                    taskPenalty = Math.min(10, (int)(medium * 5));
                    reasons.add(String.format(
                        "%d MEDIUM-priority task(s) active during leave. ", medium));
                }
            } catch (Exception e) {
                log.warn("Task impact check failed: {}", e.getMessage());
            }
        }
        score -= taskPenalty;

        // ── D. Leave type bonus ─────────────────────────────────────────────
        int bonus = 0;
        try {
            String typeName = leave.getLeaveType().name();
            if (typeName.contains("SICK") || typeName.contains("MEDICAL")
                    || typeName.contains("EMERGENCY") || typeName.contains("MATERNITY")
                    || typeName.contains("PATERNITY")) {
                bonus = 10;
                reasons.add("Medical/emergency leave type (+10 bonus). ");
            }
        } catch (Exception e) {
            log.warn("Leave type bonus check failed: {}", e.getMessage());
        }

        score = Math.min(100, Math.max(0, score + bonus));

        // ── Recommendation ──────────────────────────────────────────────────
        String recommendation;
        if (score >= 70) {
            recommendation = "APPROVE";
            if (reasons.isEmpty())
                reasons.add("No issues found. Team available, workload manageable.");
        } else if (score >= 40) {
            recommendation = "REVIEW";
            reasons.add("Recommendation: review with team leader before final decision.");
        } else {
            recommendation = "REJECT";
            reasons.add("Score too low — significant team/task impact detected.");
        }

        leave.setSystemScore(score);
        leave.setSystemRecommendation(recommendation);
        leave.setDecisionReason(String.join("", reasons).trim());

        log.info("Leave scored: score={}, recommendation={}, reason={}",
                score, recommendation, leave.getDecisionReason());
    }
}
