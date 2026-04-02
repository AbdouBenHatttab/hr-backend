package tn.isetbizerte.pfe.hrbackend.modules.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.isetbizerte.pfe.hrbackend.modules.notification.entity.Notification;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserOrderByCreatedAtDesc(User user);

    List<Notification> findByIdInAndUser(List<Long> ids, User user);

    long countByIdInAndUser(List<Long> ids, User user);
}
