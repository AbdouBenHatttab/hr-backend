package tn.isetbizerte.pfe.hrbackend.modules.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.isetbizerte.pfe.hrbackend.common.enums.TypeRole;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.Person;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.person WHERE LOWER(u.username) = LOWER(:username)")
    Optional<User> findByUsernameIgnoreCaseWithPerson(@Param("username") String username);
    Optional<User> findByKeycloakId(String keycloakId);
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.person WHERE LOWER(u.person.email) = LOWER(:email)")
    Optional<User> findByPersonEmailIgnoreCaseWithPerson(@Param("email") String email);
    @Query("SELECT u FROM User u " +
           "LEFT JOIN FETCH u.person " +
           "LEFT JOIN FETCH u.team t " +
           "LEFT JOIN FETCH t.teamLeader tl " +
           "LEFT JOIN FETCH tl.person " +
           "WHERE u.keycloakId = :keycloakId")
    Optional<User> findByKeycloakIdWithPersonAndTeamLeader(@Param("keycloakId") String keycloakId);
    Optional<User> findByPerson(Person person);
    boolean existsByUsername(String username);
    boolean existsByKeycloakId(String keycloakId);
    List<User> findByRole(TypeRole role);
    Page<User> findByRole(TypeRole role, Pageable pageable);
    List<User> findByActive(Boolean active);
    long countByRole(TypeRole role);
    long countByTeamId(Long teamId);
}
