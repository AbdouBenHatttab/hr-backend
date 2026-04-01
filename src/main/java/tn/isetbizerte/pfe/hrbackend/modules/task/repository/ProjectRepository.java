package tn.isetbizerte.pfe.hrbackend.modules.task.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.isetbizerte.pfe.hrbackend.modules.task.entity.Project;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findByTeamId(Long teamId);

    @Query("SELECT p FROM Project p LEFT JOIN FETCH p.tasks WHERE p.id = :id")
    Optional<Project> findByIdWithTasks(@Param("id") Long id);

    @Query("SELECT p FROM Project p LEFT JOIN FETCH p.tasks WHERE p.team.id = :teamId")
    List<Project> findByTeamIdWithTasks(@Param("teamId") Long teamId);
}
