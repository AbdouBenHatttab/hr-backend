package tn.isetbizerte.pfe.hrbackend.modules.hr.service;

import jakarta.mail.Message;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.oauth2.jwt.Jwt;
import tn.isetbizerte.pfe.hrbackend.common.enums.TypeRole;
import tn.isetbizerte.pfe.hrbackend.common.exception.UnauthorizedException;
import tn.isetbizerte.pfe.hrbackend.modules.hr.dto.SendHrManualEmailRequest;
import tn.isetbizerte.pfe.hrbackend.modules.hr.entity.HrManualEmailLog;
import tn.isetbizerte.pfe.hrbackend.modules.hr.entity.HrManualEmailStatus;
import tn.isetbizerte.pfe.hrbackend.modules.hr.repository.HrManualEmailLogRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.Person;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.UserRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.service.AuthenticatedUserResolver;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class HrManualEmailServiceTest {

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

        ReflectionTestUtils.setField(service, "fromEmail", "arabsoft.info@gmail.com");
        ReflectionTestUtils.setField(service, "fromDisplayName", "ArabSoft Human Resources");
        ReflectionTestUtils.setField(service, "replyToEmail", "arabsoft.hr.office@gmail.com");
    }

    @Test
    void sendsManualEmailToStoredRecipientAndStoresPreviewOnly() throws Exception {
        User sender = user("hr.manager", "Hassan", "Rami", "kc-hr", 7L, "hr@example.com");
        sender.setRole(TypeRole.HR_MANAGER);
        User recipient = user("employee.one", "Mona", "Ali", "kc-emp", 42L, "employee@example.com");

        when(authenticatedUserResolver.require(any())).thenReturn(sender);
        when(userRepository.findById(42L)).thenReturn(Optional.of(recipient));
        when(mailSender.createMimeMessage()).thenReturn(new JavaMailSenderImpl().createMimeMessage());
        doNothing().when(mailSender).send(any(MimeMessage.class));
        when(logRepository.save(any(HrManualEmailLog.class))).thenAnswer(invocation -> {
            HrManualEmailLog log = invocation.getArgument(0);
            log.setId(100L);
            return log;
        });

        String longMessage = "Please review the attached file and upload a clearer copy. ".repeat(12);
        SendHrManualEmailRequest request = new SendHrManualEmailRequest();
        request.setRecipientUserId(42L);
        request.setSubject("Missing document information");
        request.setMessage(longMessage);
        request.setReferenceType("DOCUMENT_REQUEST");
        request.setReferenceId(77L);

        var response = service.sendManualEmail(request, jwt("kc-hr", "hr.manager"));

        assertThat(response.getStatus()).isEqualTo("SENT");
        assertThat(response.getRecipientEmail()).isEqualTo("employee@example.com");
        assertThat(response.getSentByUsername()).isEqualTo("hr.manager");
        assertThat(response.getLogId()).isEqualTo(100L);

        verify(authenticatedUserResolver).require(any());
        verify(userRepository).findById(42L);
        verify(logRepository).save(any(HrManualEmailLog.class));

        var logCaptor = org.mockito.ArgumentCaptor.forClass(HrManualEmailLog.class);
        verify(logRepository).save(logCaptor.capture());
        HrManualEmailLog saved = logCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(HrManualEmailStatus.SENT);
        assertThat(saved.getRecipientEmail()).isEqualTo("employee@example.com");
        assertThat(saved.getSentByUsername()).isEqualTo("hr.manager");
        assertThat(saved.getReferenceType()).isEqualTo("DOCUMENT_REQUEST");
        assertThat(saved.getReferenceId()).isEqualTo(77L);
        assertThat(saved.getBodyPreview()).hasSize(500);
        assertThat(saved.getBodyPreview()).endsWith("...");

        var messageCaptor = org.mockito.ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        MimeMessage sent = messageCaptor.getValue();

        assertThat(((InternetAddress) sent.getFrom()[0]).getAddress()).isEqualTo("arabsoft.info@gmail.com");
        assertThat(((InternetAddress) sent.getFrom()[0]).getPersonal()).isEqualTo("ArabSoft Human Resources");
        assertThat(((InternetAddress) sent.getReplyTo()[0]).getAddress()).isEqualTo("arabsoft.hr.office@gmail.com");
        assertThat(((InternetAddress) sent.getRecipients(Message.RecipientType.TO)[0]).getAddress()).isEqualTo("employee@example.com");
        assertThat(sent.getSubject()).isEqualTo("Missing document information");
    }

    @Test
    void failedSendIsLoggedAsFailed() throws Exception {
        User sender = user("hr.manager", "Hassan", "Rami", "kc-hr", 7L, "hr@example.com");
        sender.setRole(TypeRole.HR_MANAGER);
        User recipient = user("employee.one", "Mona", "Ali", "kc-emp", 42L, "employee@example.com");

        when(authenticatedUserResolver.require(any())).thenReturn(sender);
        when(userRepository.findById(42L)).thenReturn(Optional.of(recipient));
        when(mailSender.createMimeMessage()).thenReturn(new JavaMailSenderImpl().createMimeMessage());
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(MimeMessage.class));
        when(logRepository.save(any(HrManualEmailLog.class))).thenAnswer(invocation -> {
            HrManualEmailLog log = invocation.getArgument(0);
            log.setId(101L);
            return log;
        });

        SendHrManualEmailRequest request = new SendHrManualEmailRequest();
        request.setRecipientUserId(42L);
        request.setSubject("Missing document information");
        request.setMessage("Please upload a clearer copy of your document.");

        assertThatThrownBy(() -> service.sendManualEmail(request, jwt("kc-hr", "hr.manager")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to send manual HR email");

        var logCaptor = org.mockito.ArgumentCaptor.forClass(HrManualEmailLog.class);
        verify(logRepository).save(logCaptor.capture());
        HrManualEmailLog saved = logCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(HrManualEmailStatus.FAILED);
        assertThat(saved.getErrorMessage()).contains("SMTP down");
        assertThat(saved.getSentAt()).isNull();
    }

    @Test
    void nonHrSenderIsForbidden() {
        User sender = user("employee.one", "Mona", "Ali", "kc-emp", 7L, "employee@example.com");
        sender.setRole(TypeRole.EMPLOYEE);

        when(authenticatedUserResolver.require(any())).thenReturn(sender);

        SendHrManualEmailRequest request = new SendHrManualEmailRequest();
        request.setRecipientUserId(42L);
        request.setSubject("Missing document information");
        request.setMessage("Please upload a clearer copy of your document.");

        assertThatThrownBy(() -> service.sendManualEmail(request, jwt("kc-emp", "employee.one")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Only HR_MANAGER can send manual HR emails.");

        verify(authenticatedUserResolver).require(any());
        verifyNoInteractions(userRepository, mailSender, logRepository);
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
}
