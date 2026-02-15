package tn.isetbizerte.pfe.hrbackend.modules.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.LoginHistory;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LoginHistoryRepository extends JpaRepository<LoginHistory, Long> {
    List<LoginHistory> findByUser(User user);
    List<LoginHistory> findByUserOrderByLoginDateDesc(User user);
    List<LoginHistory> findByUserAndSuccessOrderByLoginDateDesc(User user, Boolean success);
    List<LoginHistory> findByLoginDateBetween(LocalDateTime start, LocalDateTime end);
}

