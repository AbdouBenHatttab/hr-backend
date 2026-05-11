package tn.isetbizerte.pfe.hrbackend.modules.hr.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import tn.isetbizerte.pfe.hrbackend.common.exception.GlobalExceptionHandler;
import tn.isetbizerte.pfe.hrbackend.modules.hr.dto.SendHrManualEmailResponse;
import tn.isetbizerte.pfe.hrbackend.modules.hr.service.HrManualEmailService;

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

class HRManualEmailControllerTest {

    private HrManualEmailService hrManualEmailService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        hrManualEmailService = mock(HrManualEmailService.class);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        mockMvc = MockMvcBuilders
                .standaloneSetup(new HRManualEmailController(hrManualEmailService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setCustomArgumentResolvers(new StubJwtAuthenticationPrincipalResolver())
                .build();
    }

    @Test
    void hrManagerCanSendManualEmail() throws Exception {
        when(hrManualEmailService.sendManualEmail(any(), any())).thenReturn(successResponse());

        mockMvc.perform(post("/api/hr/emails/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipientUserId": 42,
                                  "subject": "Missing document information",
                                  "message": "Please upload a clearer copy of your document.",
                                  "referenceType": "DOCUMENT_REQUEST",
                                  "referenceId": 77
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.recipientUserId").value(42))
                .andExpect(jsonPath("$.recipientEmail").value("employee@example.com"));

        verify(hrManualEmailService).sendManualEmail(any(), any());
    }

    @Test
    void missingSubjectIsRejected() throws Exception {
        mockMvc.perform(post("/api/hr/emails/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipientUserId": 42,
                                  "message": "Please upload a clearer copy of your document."
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("subject: Subject is required"));

        verifyNoInteractions(hrManualEmailService);
    }

    @Test
    void missingMessageIsRejected() throws Exception {
        mockMvc.perform(post("/api/hr/emails/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipientUserId": 42,
                                  "subject": "Missing document information"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("message: Message is required"));

        verifyNoInteractions(hrManualEmailService);
    }

    @Test
    void arbitraryRecipientEmailFieldIsRejected() throws Exception {
        mockMvc.perform(post("/api/hr/emails/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipientUserId": 42,
                                  "recipientEmail": "evil@example.com",
                                  "subject": "Missing document information",
                                  "message": "Please upload a clearer copy of your document."
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(hrManualEmailService);
    }

    private SendHrManualEmailResponse successResponse() {
        SendHrManualEmailResponse response = new SendHrManualEmailResponse();
        response.setLogId(15L);
        response.setStatus("SENT");
        response.setMessage("Manual email sent successfully");
        response.setRecipientUserId(42L);
        response.setRecipientEmail("employee@example.com");
        response.setSentByUserId(7L);
        response.setSentByUsername("hr.manager");
        return response;
    }

    private static class StubJwtAuthenticationPrincipalResolver implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
                    && Jwt.class.isAssignableFrom(parameter.getParameterType());
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                      NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
            return Jwt.withTokenValue("token")
                    .header("alg", "none")
                    .subject("kc-hr")
                    .claim("preferred_username", "hr.manager")
                    .build();
        }
    }
}
