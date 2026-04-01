package tn.isetbizerte.pfe.hrbackend.modules.requests.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import tn.isetbizerte.pfe.hrbackend.modules.requests.entity.DocumentRequest;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRequestRepository extends JpaRepository<DocumentRequest, Long> {
    List<DocumentRequest> findByUserOrderByRequestedAtDesc(User user);
    Page<DocumentRequest> findByUserOrderByRequestedAtDesc(User user, Pageable pageable);
    List<DocumentRequest> findAllByOrderByRequestedAtDesc();
    Page<DocumentRequest> findAllByOrderByRequestedAtDesc(Pageable pageable);
    Optional<DocumentRequest> findByVerificationToken(String token);
}
