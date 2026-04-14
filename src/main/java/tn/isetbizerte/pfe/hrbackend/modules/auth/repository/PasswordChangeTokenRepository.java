package tn.isetbizerte.pfe.hrbackend.modules.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.isetbizerte.pfe.hrbackend.modules.auth.entity.PasswordChangeToken;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PasswordChangeTokenRepository extends JpaRepository<PasswordChangeToken, Long> {
    Optional<PasswordChangeToken> findByTokenAndUsedFalse(String token);

    boolean existsByToken(String token);

    int deleteByUserId(Long userId);

    int deleteByExpiresAtBefore(LocalDateTime now);
}

