package tn.isetbizerte.pfe.hrbackend.modules.team.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tn.isetbizerte.pfe.hrbackend.common.event.NotificationEvent;
import tn.isetbizerte.pfe.hrbackend.infrastructure.email.HREmailService;
import tn.isetbizerte.pfe.hrbackend.infrastructure.kafka.producer.NotificationEventProducer;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.Person;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;

/**
 * Performs team notification and email side effects after TeamService validation and persistence.
 */
@Service
public class TeamNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(TeamNotificationService.class);

    private final NotificationEventProducer notificationEventProducer;
    private final HREmailService emailService;

    public TeamNotificationService(NotificationEventProducer notificationEventProducer,
                                   HREmailService emailService) {
        this.notificationEventProducer = notificationEventProducer;
        this.emailService = emailService;
    }

    // TeamService owns validation and persistence; this service only performs post-decision side effects.
    void notifyTeamAssigned(User employee, Long teamId, String teamName) {
        publishTeamNotification(employee, "TEAM_ASSIGNED", teamId,
                "You have been assigned to the team: " + teamName + ".");
        sendTeamAssignedEmail(employee, teamName);
    }

    void notifyTeamChanged(User employee, Long targetTeamId, String oldTeamName, String newTeamName) {
        publishTeamNotification(employee, "TEAM_CHANGED", targetTeamId,
                "Your team assignment changed from " + oldTeamName + " to " + newTeamName + ".");
        sendTeamChangedEmail(employee, oldTeamName, newTeamName);
    }

    void notifyTeamRemoved(User employee, Long teamId, String teamName) {
        publishTeamNotification(employee, "TEAM_REMOVED", teamId,
                "You have been removed from the team: " + teamName + ".");
        sendTeamRemovedEmail(employee, teamName);
    }

    void notifyTeamLeaderAssigned(User leader, Long teamId, String teamName) {
        publishLeaderNotification(leader, "TEAM_LEADER_ASSIGNED", teamId,
                "You have been assigned as Team Leader of " + teamName + ".");
        sendTeamLeaderAssignedEmail(leader, teamName);
    }

    void notifyTeamLeaderRemoved(User leader, Long teamId, String teamName) {
        publishLeaderNotification(leader, "TEAM_LEADER_REMOVED", teamId,
                "You are no longer Team Leader of " + teamName + ".");
        sendTeamLeaderRemovedEmail(leader, teamName);
    }

    private void publishTeamNotification(User employee, String type, Long teamId, String message) {
        if (employee.getKeycloakId() == null || employee.getKeycloakId().isBlank()) return;
        try {
            notificationEventProducer.publish(new NotificationEvent(
                    employee.getKeycloakId(),
                    message,
                    type,
                    "TEAM",
                    teamId,
                    "/employee/profile"
            ));
        } catch (Exception e) {
            logger.warn("Failed to enqueue {} notification for userId={}", type, employee.getId(), e);
        }
    }

    private void publishLeaderNotification(User leader, String type, Long teamId, String message) {
        if (leader == null || leader.getKeycloakId() == null || leader.getKeycloakId().isBlank()) return;
        try {
            notificationEventProducer.publish(new NotificationEvent(
                    leader.getKeycloakId(),
                    message,
                    type,
                    "TEAM",
                    teamId,
                    "/team/members"
            ));
        } catch (Exception e) {
            logger.warn("Failed to enqueue {} notification for leader userId={}", type, leader.getId(), e);
        }
    }

    private void sendTeamAssignedEmail(User employee, String teamName) {
        Person person = employee.getPerson();
        if (person == null || person.getEmail() == null || person.getEmail().isBlank()) return;
        try {
            emailService.sendTeamAssigned(person.getEmail(), person.getFirstName(), person.getLastName(), teamName);
        } catch (Exception e) {
            logger.warn("Could not send team assignment email to userId={}", employee.getId(), e);
        }
    }

    private void sendTeamChangedEmail(User employee, String oldTeamName, String newTeamName) {
        Person person = employee.getPerson();
        if (person == null || person.getEmail() == null || person.getEmail().isBlank()) return;
        try {
            emailService.sendTeamChanged(person.getEmail(), person.getFirstName(), person.getLastName(), oldTeamName, newTeamName);
        } catch (Exception e) {
            logger.warn("Could not send team change email to userId={}", employee.getId(), e);
        }
    }

    private void sendTeamRemovedEmail(User employee, String oldTeamName) {
        Person person = employee.getPerson();
        if (person == null || person.getEmail() == null || person.getEmail().isBlank()) return;
        try {
            emailService.sendTeamRemoved(person.getEmail(), person.getFirstName(), person.getLastName(), oldTeamName);
        } catch (Exception e) {
            logger.warn("Could not send team removal email to userId={}", employee.getId(), e);
        }
    }

    private void sendTeamLeaderAssignedEmail(User leader, String teamName) {
        Person person = leader.getPerson();
        if (person == null || person.getEmail() == null || person.getEmail().isBlank()) return;
        try {
            emailService.sendTeamLeaderAssigned(person.getEmail(), person.getFirstName(), person.getLastName(), teamName);
        } catch (Exception e) {
            logger.warn("Could not send Team Leader assignment email to userId={}", leader.getId(), e);
        }
    }

    private void sendTeamLeaderRemovedEmail(User leader, String teamName) {
        Person person = leader.getPerson();
        if (person == null || person.getEmail() == null || person.getEmail().isBlank()) return;
        try {
            emailService.sendTeamLeaderRemoved(person.getEmail(), person.getFirstName(), person.getLastName(), teamName);
        } catch (Exception e) {
            logger.warn("Could not send Team Leader removal email to userId={}", leader.getId(), e);
        }
    }
}
