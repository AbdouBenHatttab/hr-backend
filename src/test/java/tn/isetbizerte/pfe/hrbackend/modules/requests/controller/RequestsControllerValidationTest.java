package tn.isetbizerte.pfe.hrbackend.modules.requests.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import tn.isetbizerte.pfe.hrbackend.common.exception.GlobalExceptionHandler;
import tn.isetbizerte.pfe.hrbackend.modules.history.service.RequestHistoryService;
import tn.isetbizerte.pfe.hrbackend.modules.requests.service.RequestsService;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RequestsControllerValidationTest {

    private RequestsService requestsService;
    private RequestHistoryService requestHistoryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        requestsService = mock(RequestsService.class);
        requestHistoryService = mock(RequestHistoryService.class);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        mockMvc = MockMvcBuilders
                .standaloneSetup(new RequestsController(requestsService, requestHistoryService))
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
                                  "authorizationType": "BUSINESS_TRIP",
                                  "startDate": "2026-04-20",
                                  "endDate": "2026-04-19",
                                  "reason": "Need a short personal permission"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("End date must be on or after start date")));

        verifyNoInteractions(requestsService);
    }

    @Test
    void getHrAuthorizations_returnsPageSuccessfully() throws Exception {
        when(requestsService.getAllAuthRequests(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(Map.of(
                "id", 1L,
                "authorizationType", "TIME_PERMISSION",
                "authorizationTypeLabel", "Time Permission",
                "status", "PENDING"
        ))));

        mockMvc.perform(get("/api/hr/authorizations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].authorizationType").value("TIME_PERMISSION"));
    }
}
