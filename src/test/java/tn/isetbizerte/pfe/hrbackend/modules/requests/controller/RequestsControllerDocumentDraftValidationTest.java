package tn.isetbizerte.pfe.hrbackend.modules.requests.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.PostMapping;
import tn.isetbizerte.pfe.hrbackend.common.enums.DocumentType;
import tn.isetbizerte.pfe.hrbackend.common.exception.GlobalExceptionHandler;
import tn.isetbizerte.pfe.hrbackend.modules.history.service.RequestHistoryService;
import tn.isetbizerte.pfe.hrbackend.modules.requests.dto.ValidateDocumentDraftResponseDto;
import tn.isetbizerte.pfe.hrbackend.modules.requests.dto.ValidateDocumentDraftRequestDto;
import tn.isetbizerte.pfe.hrbackend.modules.requests.service.RequestsService;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RequestsControllerDocumentDraftValidationTest {

    private RequestsService requestsService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        requestsService = mock(RequestsService.class);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        mockMvc = MockMvcBuilders
                .standaloneSetup(new RequestsController(requestsService, mock(RequestHistoryService.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    // -----------------------------------------------------------------------
    // Route contract
    // -----------------------------------------------------------------------

    @Test
    void validateDraftRoute_existsOnController() {
        boolean hasRoute = Arrays.stream(RequestsController.class.getDeclaredMethods())
                .anyMatch(method -> {
                    PostMapping mapping = method.getAnnotation(PostMapping.class);
                    return mapping != null
                            && Arrays.asList(mapping.value())
                            .contains(RequestApiRoutes.EMPLOYEE_DOCUMENTS_VALIDATE_DRAFT);
                });
        assertThat(hasRoute)
                .as("Expected @PostMapping for " + RequestApiRoutes.EMPLOYEE_DOCUMENTS_VALIDATE_DRAFT)
                .isTrue();
    }

    @Test
    void routeConstant_hasExpectedPath() {
        assertThat(RequestApiRoutes.EMPLOYEE_DOCUMENTS_VALIDATE_DRAFT)
                .isEqualTo("/api/employee/documents/validate-draft");
    }

    // -----------------------------------------------------------------------
    // @Valid enforcement — missing documentType
    // -----------------------------------------------------------------------

    @Test
    void missingDocumentType_returns400BeforeServiceIsCalled() throws Exception {
        mockMvc.perform(post(RequestApiRoutes.EMPLOYEE_DOCUMENTS_VALIDATE_DRAFT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("documentType: documentType is required"));

        verifyNoInteractions(requestsService);
    }

    @Test
    void unsupportedDocumentTypeValue_returns400BeforeServiceIsCalled() throws Exception {
        mockMvc.perform(post(RequestApiRoutes.EMPLOYEE_DOCUMENTS_VALIDATE_DRAFT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentType\":\"MADE_UP_TYPE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is invalid or contains unsupported values."));

        verifyNoInteractions(requestsService);
    }

    // -----------------------------------------------------------------------
    // Happy-path delegations to service
    // -----------------------------------------------------------------------

    @Test
    void validSalaryCertificate_returns200WithValidTrue() throws Exception {
        ValidateDocumentDraftResponseDto stubResponse =
                ValidateDocumentDraftResponseDto.valid(
                        DocumentType.SALARY_CERTIFICATE,
                        "UPLOADED",
                        "HR will prepare and upload this document once your request is approved.");

        when(requestsService.validateDocumentDraft(any(ValidateDocumentDraftRequestDto.class)))
                .thenReturn(stubResponse);

        mockMvc.perform(post(RequestApiRoutes.EMPLOYEE_DOCUMENTS_VALIDATE_DRAFT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentType\":\"SALARY_CERTIFICATE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.documentType").value("SALARY_CERTIFICATE"))
                .andExpect(jsonPath("$.fulfillmentMode").value("UPLOADED"));

        verify(requestsService).validateDocumentDraft(any(ValidateDocumentDraftRequestDto.class));
    }

    @Test
    void contractCopy_returns200WithValidFalseAndError() throws Exception {
        ValidateDocumentDraftResponseDto stubResponse =
                ValidateDocumentDraftResponseDto.invalid(
                        "CONTRACT_COPY is an HR-managed document and cannot be requested by employees.");

        when(requestsService.validateDocumentDraft(any(ValidateDocumentDraftRequestDto.class)))
                .thenReturn(stubResponse);

        mockMvc.perform(post(RequestApiRoutes.EMPLOYEE_DOCUMENTS_VALIDATE_DRAFT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentType\":\"CONTRACT_COPY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.errors[0]").value(
                        "CONTRACT_COPY is an HR-managed document and cannot be requested by employees."));

        verify(requestsService).validateDocumentDraft(any(ValidateDocumentDraftRequestDto.class));
    }
}
