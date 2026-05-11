package tn.isetbizerte.pfe.hrbackend.modules.hr.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.oauth2.jwt.Jwt;
import tn.isetbizerte.pfe.hrbackend.common.enums.TypeRole;
import tn.isetbizerte.pfe.hrbackend.common.exception.UnauthorizedException;
import tn.isetbizerte.pfe.hrbackend.modules.hr.entity.HrManualEmailLog;
import tn.isetbizerte.pfe.hrbackend.modules.hr.entity.HrManualEmailStatus;
import tn.isetbizerte.pfe.hrbackend.modules.hr.repository.HrManualEmailLogRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.Person;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.UserRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.service.AuthenticatedUserResolver;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class HrManualEmailAuditServiceTest {

    private JavaMailSender mailSender;
    private UserRepository userRepository;
    private AuthenticatedUserResolver authenticatedUserResolver;
    private HrManualEmailLogRepository logRepository;
    private HrManualEmailService service;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        userRepository = mock(UserRepository.class);
        authenticatedUserResolver = mock(AuthenticatedUserResolver.class);
        logRepository = mock(HrManualEmailLogRepository.class);
        service = new HrManualEmailService(mailSender, userRepository, authenticatedUserResolver, logRepository);
    }

    @Test
    void hrManagerCanListLogsNewestFirstAndFilterByStatus() {
        User sender = user("hr.manager", "Hassan", "Rami", "kc-hr", 7L, "hr@example.com");
        sender.setRole(TypeRole.HR_MANAGER);
        when(authenticatedUserResolver.require(any())).thenReturn(sender);
        when(logRepository.findAll(any(Sort.class))).thenReturn(List.of(
                log(1L, "employee@example.com", "hr.manager", "ArabSoft Human Resources", "SENT", LocalDateTime.of(2026, 5, 10, 8, 0)),
                log(2L, "other@example.com", "hr.manager", "ArabSoft Human Resources", "FAILED", LocalDateTime.of(2026, 5, 11, 8, 0)),
                log(3L, "employee@example.com", "hr.manager", "ArabSoft Human Resources", "SENT", LocalDateTime.of(2026, 5, 12, 8, 0))
        ));

        var page = service.getManualEmailLogs(
                jwt("kc-hr", "hr.manager"),
                "SENT",
                "employee@example.com",
                "hr.manager",
                "DOCUMENT_REQUEST",
                LocalDate.of(2026, 5, 10),
                LocalDate.of(2026, 5, 12),
                PageRequest.of(0, 10)
        );

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent().get(0).getId()).isEqualTo(3L);
        assertThat(page.getContent().get(1).getId()).isEqualTo(1L);
        assertThat(page.getContent().get(0).getStatus()).isEqualTo("SENT");
        assertThat(page.getContent().get(0).getRecipientEmail()).isEqualTo("employee@example.com");
        assertThat(page.getContent().get(0).getSentByDisplayName()).isEqualTo("ArabSoft Human Resources");
        assertThat(page.getContent().get(0).getBodyPreview()).isEqualTo("Quarterly follow-up");

        verify(authenticatedUserResolver).require(any());
        verify(logRepository).findAll(any(Sort.class));
    }

    @Test
    void nonHrCannotViewLogs() {
        User sender = user("employee.one", "Mona", "Ali", "kc-emp", 7L, "employee@example.com");
        sender.setRole(TypeRole.EMPLOYEE);
        when(authenticatedUserResolver.require(any())).thenReturn(sender);

        assertThatThrownBy(() -> service.getManualEmailLogs(
                jwt("kc-emp", "employee.one"),
                null,
                null,
                null,
                null,
                null,
                null,
                PageRequest.of(0, 10)
        )).isInstanceOf(UnauthorizedException.class)
                .hasMessage("Only HR_MANAGER can view manual HR email logs.");

        verify(authenticatedUserResolver).require(any());
        verifyNoInteractions(logRepository, userRepository, mailSender);
    }

    private User user(String username, String firstName, String lastName, String keycloakId, Long id, String email) {
        Person person = new Person();
        person.setFirstName(firstName);
        person.setLastName(lastName);
        person.setEmail(email);

        User user = new User(keycloakId, username);
        user.setId(id);
        user.setPerson(person);
        person.setUser(user);
        return user;
    }

    private Jwt jwt(String subject, String preferredUsername) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .claim("preferred_username", preferredUsername)
                .build();
    }

    private HrManualEmailLog log(Long id, String recipientEmail, String senderUsername, String senderDisplayName,
                                 String status, LocalDateTime createdAt) {
        HrManualEmailLog log = new HrManualEmailLog();
        log.setId(id);
        log.setRecipientUserId(42L);
        log.setRecipientEmail(recipientEmail);
        log.setSentByUserId(7L);
        log.setSentByUsername(senderUsername);
        log.setSentByDisplayName(senderDisplayName);
        log.setSubject("Quarterly follow-up");
        log.setBodyPreview("Quarterly follow-up");
        log.setReferenceType("DOCUMENT_REQUEST");
        log.setReferenceId(77L);
        log.setStatus(HrManualEmailStatus.valueOf(status));
        log.setCreatedAt(createdAt);
        log.setSentAt(createdAt);
        return log;
    }
}
