package tn.isetbizerte.pfe.hrbackend.modules.requests.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import tn.isetbizerte.pfe.hrbackend.modules.requests.entity.LoanRequest;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoanRequestRepository extends JpaRepository<LoanRequest, Long> {
    List<LoanRequest> findByUserOrderByRequestedAtDesc(User user);
    Page<LoanRequest> findByUserOrderByRequestedAtDesc(User user, Pageable pageable);
    List<LoanRequest> findAllByOrderByRequestedAtDesc();
    Page<LoanRequest> findAllByOrderByRequestedAtDesc(Pageable pageable);
    Optional<LoanRequest> findByVerificationToken(String token);
}
