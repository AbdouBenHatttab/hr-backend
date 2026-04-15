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
import tn.isetbizerte.pfe.hrbackend.common.exception.GlobalExceptionHandler;
import tn.isetbizerte.pfe.hrbackend.modules.requests.service.RequestsService;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RequestsControllerValidationTest {

    private RequestsService requestsService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        requestsService = mock(RequestsService.class);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        mockMvc = MockMvcBuilders
                .standaloneSetup(new RequestsController(requestsService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void createDocumentRequest_returnsClean400WhenDocumentTypeIsMissing() throws Exception {
        mockMvc.perform(post("/api/employee/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"Need it for bank paperwork\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("documentType: Document type is required"));

        verifyNoInteractions(requestsService);
    }

    @Test
    void createDocumentRequest_returnsClean400WhenDocumentTypeIsUnsupported() throws Exception {
        mockMvc.perform(post("/api/employee/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentType\":\"NOT_A_DOCUMENT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is invalid or contains unsupported values."));

        verifyNoInteractions(requestsService);
    }

    @Test
    void createAuthorizationRequest_returnsClean400WhenDateRangeIsInvalid() throws Exception {
        mockMvc.perform(post("/api/employee/authorizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "authorizationType": "WORK_FROM_HOME",
                                  "startDate": "2026-04-20",
                                  "endDate": "2026-04-19",
                                  "reason": "Need one day remote"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("End date must be on or after start date")));

        verifyNoInteractions(requestsService);
    }
}
