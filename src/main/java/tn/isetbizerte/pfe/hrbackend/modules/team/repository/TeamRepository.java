package tn.isetbizerte.pfe.hrbackend.modules.team.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.isetbizerte.pfe.hrbackend.modules.team.entity.Team;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeamRepository extends JpaRepository<Team, Long> {

    Optional<Team> findByTeamLeader(User teamLeader);

    boolean existsByName(String name);

    /**
     * Load a team with its leader AND leader's person in one query.
     * Does NOT fetch members — use findByIdWithMembers separately.
     */
    @Query("SELECT t FROM Team t " +
           "LEFT JOIN FETCH t.teamLeader tl " +
           "LEFT JOIN FETCH tl.person " +
           "WHERE t.id = :id")
    Optional<Team> findByIdWithDetails(@Param("id") Long id);

    /**
     * Load a team with its members AND their persons in one query.
     * Does NOT fetch leader — use findByIdWithDetails separately.
     */
    @Query("SELECT DISTINCT t FROM Team t " +
           "LEFT JOIN FETCH t.members m " +
           "LEFT JOIN FETCH m.person " +
           "WHERE t.id = :id")
    Optional<Team> findByIdWithMembers(@Param("id") Long id);

    /**
     * Find a team by team leader's Keycloak ID.
     * Only fetches leader (no members) — used for TL auth checks.
     */
    @Query("SELECT t FROM Team t " +
           "LEFT JOIN FETCH t.teamLeader tl " +
           "LEFT JOIN FETCH tl.person " +
           "WHERE tl.keycloakId = :keycloakId")
    Optional<Team> findByTeamLeaderKeycloakId(@Param("keycloakId") String keycloakId);
}
