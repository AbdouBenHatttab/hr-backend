package tn.isetbizerte.pfe.hrbackend.modules.task.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.isetbizerte.pfe.hrbackend.modules.task.entity.Task;

import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByProjectId(Long projectId);

    // All tasks assigned to a specific user
    @Query("SELECT t FROM Task t JOIN FETCH t.project WHERE t.assignee.id = :userId")
    List<Task> findByAssigneeId(@Param("userId") Long userId);

    // All tasks in a team's projects
    @Query("SELECT t FROM Task t JOIN FETCH t.project p WHERE p.team.id = :teamId")
    List<Task> findByTeamId(@Param("teamId") Long teamId);

    /**
     * Find active (IN_PROGRESS) tasks for a user that overlap with a date range.
     * Used for leave scoring — active tasks during leave = penalty.
     */
    @Query(value = "SELECT t.* FROM tasks t " +
           "WHERE t.assignee_id = :userId " +
           "AND t.status = 'IN_PROGRESS' " +
           "AND (" +
           "  (t.start_date IS NOT NULL AND t.start_date <= :endDate AND t.due_date >= :startDate) " +
           "  OR (t.start_date IS NULL AND t.due_date >= :startDate)" +
           ")",
           nativeQuery = true)
    List<Task> findActiveTasksOverlapping(@Param("userId")    Long userId,
                                          @Param("startDate") java.time.LocalDate startDate,
                                          @Param("endDate")   java.time.LocalDate endDate);
}
