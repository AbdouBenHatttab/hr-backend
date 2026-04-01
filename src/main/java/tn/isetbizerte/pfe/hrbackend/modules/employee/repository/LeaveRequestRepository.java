package tn.isetbizerte.pfe.hrbackend.modules.employee.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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
     * Required for PDF generation — avoids LazyInitializationException.
     */
    @Query("SELECT lr FROM LeaveRequest lr " +
           "JOIN FETCH lr.user u " +
           "JOIN FETCH u.person " +
           "WHERE lr.id = :id")
    Optional<LeaveRequest> findByIdWithUserAndPerson(@Param("id") Long id);

    // Used by QR verification endpoint
    Optional<LeaveRequest> findByVerificationToken(String verificationToken);

    List<LeaveRequest> findByUser(User user);
    List<LeaveRequest> findByStatus(LeaveStatus status);
    List<LeaveRequest> findByUserAndStatus(User user, LeaveStatus status);

    /**
     * Get all pending leave requests for employees in a specific team.
     * Used by Team Leader to see only their team's requests.
     */
    @Query("SELECT lr FROM LeaveRequest lr " +
           "JOIN FETCH lr.user u " +
           "JOIN FETCH u.person " +
           "WHERE u.team.id = :teamId " +
           "AND lr.status = 'PENDING'")
    List<LeaveRequest> findPendingByTeamId(@Param("teamId") Long teamId);

    /**
     * Get all leave requests (any status) for employees in a specific team.
     */
    @Query("SELECT lr FROM LeaveRequest lr " +
           "JOIN FETCH lr.user u " +
           "JOIN FETCH u.person " +
           "WHERE u.team.id = :teamId")
    List<LeaveRequest> findAllByTeamId(@Param("teamId") Long teamId);

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

