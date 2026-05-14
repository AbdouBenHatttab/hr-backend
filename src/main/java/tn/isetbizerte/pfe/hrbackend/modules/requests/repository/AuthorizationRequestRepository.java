package tn.isetbizerte.pfe.hrbackend.modules.requests.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tn.isetbizerte.pfe.hrbackend.common.enums.AuthorizationType;
import tn.isetbizerte.pfe.hrbackend.common.enums.RequestStatus;
import tn.isetbizerte.pfe.hrbackend.modules.requests.entity.AuthorizationRequest;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AuthorizationRequestRepository extends JpaRepository<AuthorizationRequest, Long> {
    List<AuthorizationRequest> findByUserOrderByRequestedAtDesc(User user);
    Page<AuthorizationRequest> findByUserOrderByRequestedAtDesc(User user, Pageable pageable);
    List<AuthorizationRequest> findAllByOrderByRequestedAtDesc();
    Page<AuthorizationRequest> findAllByOrderByRequestedAtDesc(Pageable pageable);
    Optional<AuthorizationRequest> findByVerificationToken(String token);

    @Query("SELECT ar FROM AuthorizationRequest ar " +
           "JOIN FETCH ar.user u " +
           "LEFT JOIN FETCH u.person " +
           "LEFT JOIN FETCH u.team " +
           "ORDER BY ar.requestedAt DESC")
    List<AuthorizationRequest> findAllForReportExport();

    long countByStatus(RequestStatus status);
    long countByUserAndStatus(User user, RequestStatus status);

    List<AuthorizationRequest> findByUserAndAuthorizationTypeAndStatusAndAbsenceDateBetweenOrderByAbsenceDateAscFromTimeAsc(
            User user,
            AuthorizationType authorizationType,
            RequestStatus status,
            LocalDate taskStart,
            LocalDate taskEnd
    );

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE authorization_requests
            SET authorization_type = 'TIME_PERMISSION'
            WHERE authorization_type IN ('WORK_FROM_HOME', 'OVERTIME', 'EARLY_DEPARTURE', 'LATE_ARRIVAL')
            """, nativeQuery = true)
    int normalizeLegacyAuthorizationTypes();

    @Query("""
            select count(a) > 0
            from AuthorizationRequest a
            where a.user = :user
              and a.authorizationType = :authorizationType
              and a.status = :status
              and a.absenceDate = :absenceDate
              and a.fromTime < :meetingEndTime
              and a.toTime > :meetingStartTime
            """)
    boolean existsShortAbsenceOverlap(
            @Param("user") User user,
            @Param("authorizationType") AuthorizationType authorizationType,
            @Param("status") RequestStatus status,
            @Param("absenceDate") LocalDate absenceDate,
            @Param("meetingStartTime") LocalTime meetingStartTime,
            @Param("meetingEndTime") LocalTime meetingEndTime
    );
}
