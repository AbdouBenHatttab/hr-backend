package tn.isetbizerte.pfe.hrbackend.modules.requests.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.isetbizerte.pfe.hrbackend.common.enums.RequestStatus;
import tn.isetbizerte.pfe.hrbackend.modules.requests.entity.LoanRequest;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface LoanRequestRepository extends JpaRepository<LoanRequest, Long> {
    List<LoanRequest> findByUserOrderByRequestedAtDesc(User user);
    Page<LoanRequest> findByUserOrderByRequestedAtDesc(User user, Pageable pageable);
    List<LoanRequest> findAllByOrderByRequestedAtDesc();
    Page<LoanRequest> findAllByOrderByRequestedAtDesc(Pageable pageable);
    Optional<LoanRequest> findByVerificationToken(String token);

    @Query("SELECT lr FROM LoanRequest lr " +
           "JOIN FETCH lr.user u " +
           "LEFT JOIN FETCH u.person " +
           "LEFT JOIN FETCH u.team " +
           "ORDER BY lr.requestedAt DESC")
    List<LoanRequest> findAllForReportExport();

    long countByStatus(RequestStatus status);
    long countByUserAndStatus(User user, RequestStatus status);
    long countByStatusAndAttachmentStoragePathIsNull(RequestStatus status);
    long countByStatusAndAttachmentStoragePath(RequestStatus status, String attachmentStoragePath);
    long countByUserAndStatusAndAttachmentStoragePathIsNull(User user, RequestStatus status);
    long countByUserAndStatusAndAttachmentStoragePath(User user, RequestStatus status, String attachmentStoragePath);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from LoanRequest l where l.id = :id")
    Optional<LoanRequest> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select count(l) > 0
            from LoanRequest l
            where l.id <> :loanId
              and l.status = :status
              and l.meetingScheduledBy = :meetingScheduledBy
              and l.meetingAt = :meetingAt
            """)
    boolean existsScheduledMeetingConflictForHr(
            @Param("loanId") Long loanId,
            @Param("meetingScheduledBy") String meetingScheduledBy,
            @Param("meetingAt") LocalDateTime meetingAt,
            @Param("status") RequestStatus status
    );

    @Query("""
            select count(l) > 0
            from LoanRequest l
            where l.id <> :loanId
              and l.status = :status
              and l.user = :user
              and l.meetingAt = :meetingAt
            """)
    boolean existsScheduledMeetingConflictForEmployee(
            @Param("loanId") Long loanId,
            @Param("user") User user,
            @Param("meetingAt") LocalDateTime meetingAt,
            @Param("status") RequestStatus status
    );
}
