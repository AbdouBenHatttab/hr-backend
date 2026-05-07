package tn.isetbizerte.pfe.hrbackend.modules.requests.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tn.isetbizerte.pfe.hrbackend.common.enums.DocumentType;
import tn.isetbizerte.pfe.hrbackend.infrastructure.kafka.producer.RequestEventProducer;
import tn.isetbizerte.pfe.hrbackend.infrastructure.storage.DocumentAttachmentStorageService;
import tn.isetbizerte.pfe.hrbackend.modules.calendar.service.WorkingDayService;
import tn.isetbizerte.pfe.hrbackend.modules.employee.repository.LeaveRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.history.service.RequestHistoryService;
import tn.isetbizerte.pfe.hrbackend.modules.requests.dto.ValidateDocumentDraftRequestDto;
import tn.isetbizerte.pfe.hrbackend.modules.requests.dto.ValidateDocumentDraftResponseDto;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.AuthorizationRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.DocumentRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.LoanRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.StoredEmployeeDocumentRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.PersonRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.UserRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.service.AuthenticatedUserResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RequestsServiceDocumentDraftValidationTest {

    private RequestsService service;

    @BeforeEach
    void setUp() {
        service = new RequestsService(
                mock(DocumentRequestRepository.class),
                mock(StoredEmployeeDocumentRepository.class),
                mock(LoanRequestRepository.class),
                mock(AuthorizationRequestRepository.class),
                mock(UserRepository.class),
                mock(PersonRepository.class),
                mock(AuthenticatedUserResolver.class),
                mock(LoanScoreEngine.class),
                mock(RequestEventProducer.class),
                mock(RequestHistoryService.class),
                mock(DocumentAttachmentStorageService.class),
                mock(LeaveRequestRepository.class),
                mock(WorkingDayService.class)
        );
    }

    @Test
    void validSalaryCertificate_returnsValidWithUploadedFulfillment() {
        ValidateDocumentDraftRequestDto req = new ValidateDocumentDraftRequestDto();
        req.setDocumentType(DocumentType.SALARY_CERTIFICATE);

        ValidateDocumentDraftResponseDto result = service.validateDocumentDraft(req);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getDocumentType()).isEqualTo(DocumentType.SALARY_CERTIFICATE);
        assertThat(result.getFulfillmentMode()).isEqualTo("UPLOADED");
        assertThat(result.getMessage()).contains("HR will prepare and upload");
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void validLeaveBalanceStatement_returnsValidWithGeneratedFulfillment() {
        ValidateDocumentDraftRequestDto req = new ValidateDocumentDraftRequestDto();
        req.setDocumentType(DocumentType.LEAVE_BALANCE_STATEMENT);

        ValidateDocumentDraftResponseDto result = service.validateDocumentDraft(req);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getDocumentType()).isEqualTo(DocumentType.LEAVE_BALANCE_STATEMENT);
        assertThat(result.getFulfillmentMode()).isEqualTo("GENERATED");
        assertThat(result.getMessage()).contains("generated automatically");
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void contractCopy_returnsInvalidWithExplanation() {
        ValidateDocumentDraftRequestDto req = new ValidateDocumentDraftRequestDto();
        req.setDocumentType(DocumentType.CONTRACT_COPY);

        ValidateDocumentDraftResponseDto result = service.validateDocumentDraft(req);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0)).contains("HR-managed");
        assertThat(result.getFulfillmentMode()).isNull();
    }

    @Test
    void nullDocumentType_returnsInvalid() {
        ValidateDocumentDraftRequestDto req = new ValidateDocumentDraftRequestDto();
        // documentType left null

        ValidateDocumentDraftResponseDto result = service.validateDocumentDraft(req);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0)).contains("documentType is required");
    }

    @Test
    void allOtherAllowedTypes_returnValidWithUploadedFulfillment() {
        for (DocumentType type : new DocumentType[]{
                DocumentType.EMPLOYMENT_CERTIFICATE,
                DocumentType.WORK_REFERENCE_LETTER,
                DocumentType.EXPERIENCE_CERTIFICATE,
                DocumentType.CUSTOM_ADMINISTRATIVE_LETTER
        }) {
            ValidateDocumentDraftRequestDto req = new ValidateDocumentDraftRequestDto();
            req.setDocumentType(type);

            ValidateDocumentDraftResponseDto result = service.validateDocumentDraft(req);

            assertThat(result.isValid())
                    .as("Expected valid=true for type %s", type)
                    .isTrue();
            assertThat(result.getFulfillmentMode())
                    .as("Expected UPLOADED for type %s", type)
                    .isEqualTo("UPLOADED");
        }
    }
}
