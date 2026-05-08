package tn.isetbizerte.pfe.hrbackend.modules.requests.controller;

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
import tn.isetbizerte.pfe.hrbackend.common.enums.LoanType;
import tn.isetbizerte.pfe.hrbackend.common.exception.GlobalExceptionHandler;
import tn.isetbizerte.pfe.hrbackend.modules.history.service.RequestHistoryService;
import tn.isetbizerte.pfe.hrbackend.modules.requests.dto.ValidateLoanDraftRequestDto;
import tn.isetbizerte.pfe.hrbackend.modules.requests.dto.ValidateLoanDraftResponseDto;
import tn.isetbizerte.pfe.hrbackend.modules.requests.service.RequestsService;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Standalone MockMvc tests for POST /api/employee/loans/validate-draft.
 *
 * Uses the same StubJwtAuthenticationPrincipalResolver pattern from
 * RequestsControllerAuthorizationDraftValidationTest so that the
 * @AuthenticationPrincipal Jwt parameter is resolved without a full
 * Spring Security context.
 */
class RequestsControllerLoanDraftValidationTest {

    private static final LoanType VALID_LOAN_TYPE = LoanType.values()[0];

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
                .setCustomArgumentResolvers(new StubJwtAuthenticationPrincipalResolver())
                .build();
    }

    // -------------------------------------------------------------------------
    // Test 1: route constant equals /api/employee/loans/validate-draft
    // -------------------------------------------------------------------------

    @Test
    void routeConstant_hasExpectedPath() {
        assertThat(RequestApiRoutes.EMPLOYEE_LOANS_VALIDATE_DRAFT)
                .isEqualTo("/api/employee/loans/validate-draft");
    }

    // -------------------------------------------------------------------------
    // Test 2: POST mapping exists on controller
    // -------------------------------------------------------------------------

    @Test
    void validateDraftRoute_postMappingExists() {
        boolean hasRoute = Arrays.stream(RequestsController.class.getDeclaredMethods())
                .anyMatch(method -> {
                    PostMapping mapping = method.getAnnotation(PostMapping.class);
                    return mapping != null
                            && Arrays.asList(mapping.value())
                            .contains(RequestApiRoutes.EMPLOYEE_LOANS_VALIDATE_DRAFT);
                });
        assertThat(hasRoute)
                .as("Expected @PostMapping for " + RequestApiRoutes.EMPLOYEE_LOANS_VALIDATE_DRAFT)
                .isTrue();
    }

    // -------------------------------------------------------------------------
    // Test 3: valid request delegates to service and returns 200
    // -------------------------------------------------------------------------

    @Test
    void validRequest_returns200AndDelegatesToService() throws Exception {
        ValidateLoanDraftResponseDto stub = ValidateLoanDraftResponseDto.valid(
                "Your loan request draft passes all eligibility checks.");
        stub.setLoanType(VALID_LOAN_TYPE);
        stub.setAmount(new BigDecimal("5000"));
        stub.setRepaymentMonths(12);
        stub.setSalary(new BigDecimal("3000"));
        stub.setMaxEligibleAmount(new BigDecimal("9000"));
        stub.setSystemRecommendation("APPROVE");
        stub.setRiskScore(80);

        when(requestsService.validateLoanDraft(any(ValidateLoanDraftRequestDto.class), any()))
                .thenReturn(stub);

        String payload = """
            {
              "amount": 5000,
              "type": "%s",
              "reason": "Home renovation",
              "repaymentMonths": 12
            }
            """.formatted(VALID_LOAN_TYPE.name());

        mockMvc.perform(post(RequestApiRoutes.EMPLOYEE_LOANS_VALIDATE_DRAFT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.systemRecommendation").value("APPROVE"));

        verify(requestsService).validateLoanDraft(any(), any());
    }

    // -------------------------------------------------------------------------
    // Test 4: service returns valid=false, HTTP 200 with valid=false in body
    // -------------------------------------------------------------------------

    @Test
    void serviceInvalidResponse_returns200WithValidFalse() throws Exception {
        ValidateLoanDraftResponseDto stub = ValidateLoanDraftResponseDto.invalid(
                List.of("Your salary has not been registered. Contact HR."));

        when(requestsService.validateLoanDraft(any(ValidateLoanDraftRequestDto.class), any()))
                .thenReturn(stub);

        mockMvc.perform(post(RequestApiRoutes.EMPLOYEE_LOANS_VALIDATE_DRAFT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": 3000,
                                  "type": "%s",
                                  "reason": "Need funds",
                                  "repaymentMonths": 6
                                }
                                """.formatted(VALID_LOAN_TYPE.name())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.errors[0]").value("Your salary has not been registered. Contact HR."));
    }

    // -------------------------------------------------------------------------
    // Test 5: missing required DTO fields returns 400 before service is called
    // -------------------------------------------------------------------------

    @Test
    void missingRequiredFields_returns400BeforeServiceIsCalled() throws Exception {
        // Empty JSON — amount, type, reason, repaymentMonths all missing
        mockMvc.perform(post(RequestApiRoutes.EMPLOYEE_LOANS_VALIDATE_DRAFT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(requestsService);
    }

    @Test
    void missingRepaymentMonths_returns400BeforeServiceIsCalled() throws Exception {
        mockMvc.perform(post(RequestApiRoutes.EMPLOYEE_LOANS_VALIDATE_DRAFT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": 3000,
                                  "type": "%s",
                                  "reason": "Need funds"
                                }
                                """.formatted(VALID_LOAN_TYPE.name())))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(requestsService);
    }

    // -------------------------------------------------------------------------
    // Test 6: @AuthenticationPrincipal Jwt is resolved correctly (stub pattern)
    // -------------------------------------------------------------------------

    @Test
    void jwtIsResolvedCorrectly_serviceReceivesNonNullJwt() throws Exception {
        ValidateLoanDraftResponseDto stub = ValidateLoanDraftResponseDto.valid("OK");

        when(requestsService.validateLoanDraft(any(ValidateLoanDraftRequestDto.class), any(Jwt.class)))
                .thenReturn(stub);

        mockMvc.perform(post(RequestApiRoutes.EMPLOYEE_LOANS_VALIDATE_DRAFT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": 3000,
                                  "type": "%s",
                                  "reason": "Need funds",
                                  "repaymentMonths": 6
                                }
                                """.formatted(VALID_LOAN_TYPE.name())))
                .andExpect(status().isOk());

        verify(requestsService).validateLoanDraft(any(ValidateLoanDraftRequestDto.class),
                argThat(jwt -> jwt != null && "kc-test-user".equals(jwt.getSubject())));
    }

    // -------------------------------------------------------------------------
    // Test infrastructure — same pattern as authorization draft validation test
    // -------------------------------------------------------------------------

    /**
     * Resolves @AuthenticationPrincipal Jwt parameters to a stub Jwt in
     * standalone MockMvc tests where Spring Security is not active.
     *
     * Mirrors the pattern from RequestsControllerAuthorizationDraftValidationTest.
     */
    private static final class StubJwtAuthenticationPrincipalResolver
            implements HandlerMethodArgumentResolver {

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
                    && Jwt.class.isAssignableFrom(parameter.getParameterType());
        }

        @Override
        public Object resolveArgument(MethodParameter parameter,
                                      ModelAndViewContainer mavContainer,
                                      NativeWebRequest webRequest,
                                      WebDataBinderFactory binderFactory) {
            return Jwt.withTokenValue("token")
                    .header("alg", "none")
                    .claim("sub", "kc-test-user")
                    .claim("preferred_username", "test-user")
                    .build();
        }
    }
}
