package tn.isetbizerte.pfe.hrbackend.modules.requests.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tn.isetbizerte.pfe.hrbackend.common.enums.TypeRole;
import tn.isetbizerte.pfe.hrbackend.common.exception.BadRequestException;
import tn.isetbizerte.pfe.hrbackend.infrastructure.kafka.producer.RequestEventProducer;
import tn.isetbizerte.pfe.hrbackend.infrastructure.storage.DocumentAttachmentStorageService;
import tn.isetbizerte.pfe.hrbackend.infrastructure.storage.StoredAttachment;
import tn.isetbizerte.pfe.hrbackend.modules.calendar.service.WorkingDayService;
import tn.isetbizerte.pfe.hrbackend.modules.employee.repository.LeaveRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.history.service.RequestHistoryService;
import tn.isetbizerte.pfe.hrbackend.modules.requests.entity.StoredEmployeeDocument;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.AuthorizationRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.DocumentRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.LoanRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.StoredEmployeeDocumentRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.Person;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.PersonRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.UserRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.service.AuthenticatedUserResolver;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequestsServiceStoredEmployeeDocumentTest {

    private StoredEmployeeDocumentRepository storedDocumentRepo;
    private UserRepository userRepository;
    private DocumentAttachmentStorageService attachmentStorage;
    private RequestsService service;

    @BeforeEach
    void setUp() {
        storedDocumentRepo = mock(StoredEmployeeDocumentRepository.class);
        userRepository = mock(UserRepository.class);
        attachmentStorage = mock(DocumentAttachmentStorageService.class);

        service = new RequestsService(
                mock(DocumentRequestRepository.class),
                storedDocumentRepo,
                mock(LoanRequestRepository.class),
                mock(AuthorizationRequestRepository.class),
                userRepository,
                mock(PersonRepository.class),
                mock(AuthenticatedUserResolver.class),
                mock(LoanScoreEngine.class),
                mock(RequestEventProducer.class),
                mock(RequestHistoryService.class),
                attachmentStorage,
                mock(LeaveRequestRepository.class),
                mock(WorkingDayService.class)
        );
    }

    @Test
    void uploadStoredEmployeeDocument_rejectsSelfContractUpload() throws Exception {
        User hr = user(1L, "hr", "kc-hr", TypeRole.HR_MANAGER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(hr));

        assertThatThrownBy(() -> service.uploadStoredEmployeeDocument(
                1L,
                "CONTRACT_COPY",
                "",
                "kc-hr",
                "contract.pdf",
                "application/pdf",
                pdfBytes()
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("You cannot upload or replace your own HR-managed contract copy.");

        verify(attachmentStorage, never()).store(anyLong(), anyString(), anyString(), any());
        verify(storedDocumentRepo, never()).save(any());
    }

    @Test
    void uploadStoredEmployeeDocument_allowsOtherEligibleEmployee() throws Exception {
        User employee = user(2L, "employee", "kc-employee", TypeRole.EMPLOYEE);
        when(userRepository.findById(2L)).thenReturn(Optional.of(employee));
        when(storedDocumentRepo.findByEmployeeAndDocumentTypeAndActiveTrueOrderByUploadedAtDesc(any(), any()))
                .thenReturn(List.of());
        when(attachmentStorage.store(anyLong(), anyString(), anyString(), any()))
                .thenReturn(new StoredAttachment("stored/path", "contract.pdf", "application/pdf", 12L, "sha"));

        var result = service.uploadStoredEmployeeDocument(
                2L,
                "CONTRACT_COPY",
                "signed",
                "kc-hr",
                "contract.pdf",
                "application/pdf",
                pdfBytes()
        );

        assertThat(result).containsEntry("employeeId", 2L);
        assertThat(result).containsEntry("documentType", "CONTRACT_COPY");
        verify(storedDocumentRepo).save(any(StoredEmployeeDocument.class));
        verify(attachmentStorage).store(anyLong(), anyString(), anyString(), any());
    }

    private User user(Long id, String username, String keycloakId, TypeRole role) {
        Person person = new Person();
        person.setFirstName("Test");
        person.setLastName("User");
        person.setEmail(username + "@example.com");

        User user = new User(keycloakId, username);
        user.setId(id);
        user.setRole(role);
        user.setPerson(person);
        person.setUser(user);
        return user;
    }

    private byte[] pdfBytes() {
        return "%PDF-1.4 test".getBytes();
    }
}
