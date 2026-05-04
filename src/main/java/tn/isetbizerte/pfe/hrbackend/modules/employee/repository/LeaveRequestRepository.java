package tn.isetbizerte.pfe.hrbackend.modules.employee.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.isetbizerte.pfe.hrbackend.common.enums.LeaveStatus;
import tn.isetbizerte.pfe.hrbackend.modules.employee.entity.LeaveRequest;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;

import java.util.List;
import java.util.Optional;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    /**
     * Fetches a leave request with user AND person eagerly loaded in one query.
     * Required for PDF generation - avoids LazyInitializationException.
     */
    @Query("SELECT lr FROM LeaveRequest lr " +
           "JOIN FETCH lr.user u " +
           "JOIN FETCH u.person " +
           "WHERE lr.id = :id")
    Optional<LeaveRequest> findByIdWithUserAndPerson(@Param("id") Long id);

    // Used by QR verification endpoint
    Optional<LeaveRequest> findByVerificationToken(String verificationToken);

    List<LeaveRequest> findByUser(User user);
    Page<LeaveRequest> findByUser(User user, Pageable pageable);
    List<LeaveRequest> findByStatus(LeaveStatus status);
    List<LeaveRequest> findByUserAndStatus(User user, LeaveStatus status);

    long countByStatusAndTeamLeaderDecisionAndHrDecision(
            LeaveStatus status,
            tn.isetbizerte.pfe.hrbackend.common.enums.ApprovalDecision teamLeaderDecision,
            tn.isetbizerte.pfe.hrbackend.common.enums.ApprovalDecision hrDecision
    );

    long countByUserAndStatus(User user, LeaveStatus status);

    @Query("SELECT COUNT(lr) > 0 FROM LeaveRequest lr " +
           "WHERE lr.user.id = :userId " +
           "AND lr.status = 'PENDING' " +
           "AND lr.teamLeaderDecision = 'PENDING'")
    boolean existsPendingTeamLeaderApprovalByUserId(@Param("userId") Long userId);

    /**
     * Get all pending leave requests for employees in a specific team.
     * Used by Team Leader to see only their team's requests.
     */
    @Query("SELECT lr FROM LeaveRequest lr " +
           "JOIN FETCH lr.user u " +
           "JOIN FETCH u.person " +
           "WHERE u.team.id = :teamId " +
           "AND lr.status = 'PENDING'")
    Page<LeaveRequest> findPendingByTeamId(@Param("teamId") Long teamId, Pageable pageable);

    /**
     * Get all Team Leader-pending leave requests for a team, excluding the team leader.
     * This keeps Pending HR requests out of TL dashboards and widgets.
     */
    @Query("SELECT lr FROM LeaveRequest lr " +
           "JOIN FETCH lr.user u " +
           "JOIN FETCH u.person " +
           "WHERE u.team.id = :teamId " +
           "AND lr.status = 'PENDING' " +
           "AND lr.teamLeaderDecision = 'PENDING' " +
           "AND u.keycloakId <> :leaderKeycloakId " +
           "AND u.role <> 'TEAM_LEADER' " +
           "ORDER BY lr.requestDate DESC")
    Page<LeaveRequest> findPendingByTeamIdExcludingLeader(@Param("teamId") Long teamId,
                                                         @Param("leaderKeycloakId") String leaderKeycloakId,
                                                         Pageable pageable);

    /**
     * Get all leave requests (any status) for employees in a specific team.
     */
    @Query("SELECT lr FROM LeaveRequest lr " +
           "JOIN FETCH lr.user u " +
           "JOIN FETCH u.person " +
           "WHERE u.team.id = :teamId")
    Page<LeaveRequest> findAllByTeamId(@Param("teamId") Long teamId, Pageable pageable);

    /**
     * Get all leave requests (any status) for employees in a specific team (list variant).
     */
    @Query("SELECT lr FROM LeaveRequest lr " +
           "JOIN FETCH lr.user u " +
           "JOIN FETCH u.person " +
           "WHERE u.team.id = :teamId")
    List<LeaveRequest> findAllByTeamId(@Param("teamId") Long teamId);

    /**
     * HR overview query.
     * Keeps rows action-ready by surfacing exact HR-pending requests first, then most recent requests.
     */
    @Query(
            value = "SELECT lr FROM LeaveRequest lr " +
                    "JOIN FETCH lr.user u " +
                    "JOIN FETCH u.person " +
                    "ORDER BY CASE " +
                    "WHEN lr.status = 'PENDING' " +
                    " AND lr.teamLeaderDecision = 'APPROVED' " +
                    " AND lr.hrDecision = 'PENDING' THEN 0 " +
                    "ELSE 1 END, " +
                    "lr.requestDate DESC",
            countQuery = "SELECT COUNT(lr) FROM LeaveRequest lr"
    )
    Page<LeaveRequest> findAllForHrOverview(Pageable pageable);

    /**
     * Calendar - overlap date range for a specific user.
     * Includes leaves that cross the start/end boundaries.
     */
    @Query("SELECT lr FROM LeaveRequest lr " +
           "JOIN FETCH lr.user u " +
           "JOIN FETCH u.person " +
           "WHERE u = :user " +
           "AND lr.startDate <= :endDate " +
           "AND lr.endDate >= :startDate " +
           "AND lr.status IN :statuses")
    List<LeaveRequest> findByUserAndDateRangeAndStatusIn(@Param("user") User user,
                                                         @Param("startDate") java.time.LocalDate startDate,
                                                         @Param("endDate") java.time.LocalDate endDate,
                                                         @Param("statuses") List<LeaveStatus> statuses);

    /**
     * Calendar - overlap date range for a specific user id.
     */
    @Query("SELECT lr FROM LeaveRequest lr " +
           "JOIN FETCH lr.user u " +
           "JOIN FETCH u.person " +
           "WHERE u.id = :userId " +
           "AND lr.startDate <= :endDate " +
           "AND lr.endDate >= :startDate " +
           "AND lr.status IN :statuses")
    List<LeaveRequest> findByUserIdAndDateRangeAndStatusIn(@Param("userId") Long userId,
                                                           @Param("startDate") java.time.LocalDate startDate,
                                                           @Param("endDate") java.time.LocalDate endDate,
                                                           @Param("statuses") List<LeaveStatus> statuses);

    /**
     * Calendar - overlap date range for a team.
     * Includes leaves that cross the start/end boundaries.
     */
    @Query("SELECT lr FROM LeaveRequest lr " +
           "JOIN FETCH lr.user u " +
           "JOIN FETCH u.person " +
           "WHERE u.team.id = :teamId " +
           "AND lr.startDate <= :endDate " +
           "AND lr.endDate >= :startDate " +
           "AND lr.status IN :statuses")
    List<LeaveRequest> findByTeamIdAndDateRangeAndStatusIn(@Param("teamId") Long teamId,
                                                           @Param("startDate") java.time.LocalDate startDate,
                                                           @Param("endDate") java.time.LocalDate endDate,
                                                           @Param("statuses") List<LeaveStatus> statuses);

    /**
     * Calendar - overlap date range for a team and specific user.
     */
    @Query("SELECT lr FROM LeaveRequest lr " +
           "JOIN FETCH lr.user u " +
           "JOIN FETCH u.person " +
           "WHERE u.team.id = :teamId " +
           "AND u.id = :userId " +
           "AND lr.startDate <= :endDate " +
           "AND lr.endDate >= :startDate " +
           "AND lr.status IN :statuses")
    List<LeaveRequest> findByTeamIdAndUserIdAndDateRangeAndStatusIn(@Param("teamId") Long teamId,
                                                                    @Param("userId") Long userId,
                                                                    @Param("startDate") java.time.LocalDate startDate,
                                                                    @Param("endDate") java.time.LocalDate endDate,
                                                                    @Param("statuses") List<LeaveStatus> statuses);

    /**
     * Calendar - overlap date range for all users (HR).
     * Includes leaves that cross the start/end boundaries.
     */
    @Query("SELECT lr FROM LeaveRequest lr " +
           "JOIN FETCH lr.user u " +
           "JOIN FETCH u.person " +
           "WHERE lr.startDate <= :endDate " +
           "AND lr.endDate >= :startDate " +
           "AND lr.status IN :statuses")
    List<LeaveRequest> findByDateRangeAndStatusIn(@Param("startDate") java.time.LocalDate startDate,
                                                  @Param("endDate") java.time.LocalDate endDate,
                                                  @Param("statuses") List<LeaveStatus> statuses);

    /**
     * Calendar - overlap date range for a specific user (HR filter).
     */
    @Query("SELECT lr FROM LeaveRequest lr " +
           "JOIN FETCH lr.user u " +
           "JOIN FETCH u.person " +
           "WHERE u.id = :userId " +
           "AND lr.startDate <= :endDate " +
           "AND lr.endDate >= :startDate " +
           "AND lr.status IN :statuses")
    List<LeaveRequest> findByDateRangeAndStatusInAndUserId(@Param("startDate") java.time.LocalDate startDate,
                                                           @Param("endDate") java.time.LocalDate endDate,
                                                           @Param("statuses") List<LeaveStatus> statuses,
                                                           @Param("userId") Long userId);

    /**
     * Get all leave requests for a team, excluding the team leader.
     */
    @Query("SELECT lr FROM LeaveRequest lr " +
           "JOIN FETCH lr.user u " +
           "JOIN FETCH u.person " +
           "WHERE u.team.id = :teamId " +
           "AND u.keycloakId <> :leaderKeycloakId " +
           "AND u.role <> 'TEAM_LEADER' " +
           "ORDER BY lr.requestDate DESC")
    Page<LeaveRequest> findAllByTeamIdExcludingLeader(@Param("teamId") Long teamId,
                                                     @Param("leaderKeycloakId") String leaderKeycloakId,
                                                     Pageable pageable);

    /**
     * Count APPROVED/PENDING leaves for a team that overlap with [start, end].
     * Used for team availability check in scoring.
     */
    @Query(value = "SELECT COUNT(lr.id) FROM leave_requests lr " +
           "JOIN users u ON lr.user_id = u.id " +
           "WHERE u.team_id = :teamId " +
           "AND lr.status IN ('PENDING','APPROVED') " +
           "AND lr.start_date <= :endDate " +
           "AND lr.end_date   >= :startDate",
           nativeQuery = true)
    long countOverlappingByTeam(@Param("teamId") Long teamId,
                                @Param("startDate") java.time.LocalDate startDate,
                                @Param("endDate")   java.time.LocalDate endDate);

    /**
     * Sum of leave days taken by this user in the last 12 months.
     */
    @Query(value = "SELECT COALESCE(SUM(lr.number_of_days), 0) FROM leave_requests lr " +
           "WHERE lr.user_id = :userId " +
           "AND lr.status = 'APPROVED' " +
           "AND lr.start_date >= :since",
           nativeQuery = true)
    int sumLeaveDaysSince(@Param("userId") Long userId,
                          @Param("since")  java.time.LocalDate since);

    /**
     * Average leave days taken per person in the team in the last 12 months.
     * Uses native SQL because JPQL does not support AVG over a derived subquery.
     */
    @Query(value = "SELECT COALESCE(AVG(sub.total), 0) FROM " +
           "(SELECT SUM(lr.number_of_days) as total FROM leave_requests lr " +
           " JOIN users u ON lr.user_id = u.id " +
           " WHERE u.team_id = :teamId " +
           " AND lr.status = 'APPROVED' " +
           " AND lr.start_date >= :since " +
           " GROUP BY lr.user_id) sub",
           nativeQuery = true)
    double avgLeaveDaysPerTeamMember(@Param("teamId") Long teamId,
                                     @Param("since")   java.time.LocalDate since);
}
