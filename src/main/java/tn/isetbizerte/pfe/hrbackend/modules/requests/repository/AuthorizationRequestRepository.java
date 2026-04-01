package tn.isetbizerte.pfe.hrbackend.modules.requests.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import tn.isetbizerte.pfe.hrbackend.modules.requests.entity.AuthorizationRequest;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;

import java.util.List;
import java.util.Optional;

@Repository
public interface AuthorizationRequestRepository extends JpaRepository<AuthorizationRequest, Long> {
    List<AuthorizationRequest> findByUserOrderByRequestedAtDesc(User user);
    Page<AuthorizationRequest> findByUserOrderByRequestedAtDesc(User user, Pageable pageable);
    List<AuthorizationRequest> findAllByOrderByRequestedAtDesc();
    Page<AuthorizationRequest> findAllByOrderByRequestedAtDesc(Pageable pageable);
    Optional<AuthorizationRequest> findByVerificationToken(String token);
}
