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
import tn.isetbizerte.pfe.hrbackend.common.enums.AuthorizationType;
import tn.isetbizerte.pfe.hrbackend.common.exception.GlobalExceptionHandler;
import tn.isetbizerte.pfe.hrbackend.modules.history.service.RequestHistoryService;
import tn.isetbizerte.pfe.hrbackend.modules.requests.dto.ValidateAuthorizationDraftRequestDto;
import tn.isetbizerte.pfe.hrbackend.modules.requests.dto.ValidateAuthorizationDraftResponseDto;
import tn.isetbizerte.pfe.hrbackend.modules.requests.service.RequestsService;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc tests for the validate-draft authorization endpoint.
 *
 * <h2>Note on Jwt resolution in standalone MockMvc</h2>
 * The validate-draft controller method declares
 * {@code @AuthenticationPrincipal Jwt jwt}. In a full Spring Security context
 * the {@code AuthenticationPrincipalArgumentResolver} would supply this from
 * the {@code SecurityContext}. Standalone MockMvc does not load Spring
 * Security, so without help it falls through to {@code @ModelAttribute}
 * binding and tries to build a {@link Jwt} via reflection — which fails with
 * {@code "tokenValue cannot be empty"}.
 *
 * To keep these tests pure controller-layer (no Spring context, no security
 * filter chain), this class registers a tiny custom
 * {@link HandlerMethodArgumentResolver} that hands a stub {@link Jwt} to any
 * parameter annotated with {@link AuthenticationPrincipal} whose type is
 * {@code Jwt}. The service is mocked, so the Jwt content never reaches real
 * code — only the non-null contract matters.
 *
 * <h2>What is covered</h2>
 *  - Route contract (constant + @PostMapping presence)
 *  - @Valid enforcement (missing authorizationType → HTTP 400 before service)
 *  - Happy-path delegation to service (TIME_PERMISSION valid)
 *  - Blocked types return valid=false
 *  - Equipment valid case
 *  - No DB / Kafka / history side effects (service not called for invalid JSON)
 */
class RequestsControllerAuthorizationDraftValidationTest {

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
    // Route contract
    // -------------------------------------------------------------------------

    @Test
    void validateDraftRoute_existsOnController() {
        boolean hasRoute = Arrays.stream(RequestsController.class.getDeclaredMethods())
                .anyMatch(method -> {
                    PostMapping mapping = method.getAnnotation(PostMapping.class);
                    return mapping != null
                            && Arrays.asList(mapping.value())
                            .contains(RequestApiRoutes.EMPLOYEE_AUTHORIZATIONS_VALIDATE_DRAFT);
                });
        assertThat(hasRoute)
                .as("Expected @PostMapping for " + RequestApiRoutes.EMPLOYEE_AUTHORIZATIONS_VALIDATE_DRAFT)
                .isTrue();
    }

    @Test
    void routeConstant_hasExpectedPath() {
        assertThat(RequestApiRoutes.EMPLOYEE_AUTHORIZATIONS_VALIDATE_DRAFT)
                .isEqualTo("/api/employee/authorizations/validate-draft");
    }

    // -------------------------------------------------------------------------
    // @Valid enforcement — missing authorizationType
    // -------------------------------------------------------------------------

    @Test
    void missingAuthorizationType_returns400BeforeServiceIsCalled() throws Exception {
        mockMvc.perform(post(RequestApiRoutes.EMPLOYEE_AUTHORIZATIONS_VALIDATE_DRAFT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(requestsService);
    }

    @Test
    void unsupportedAuthorizationTypeValue_returns400BeforeServiceIsCalled() throws Exception {
        mockMvc.perform(post(RequestApiRoutes.EMPLOYEE_AUTHORIZATIONS_VALIDATE_DRAFT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"authorizationType\":\"MADE_UP_TYPE\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(requestsService);
    }

    // -------------------------------------------------------------------------
    // Happy path — valid TIME_PERMISSION
    // -------------------------------------------------------------------------

    @Test
    void validTimePermission_returns200WithValidTrue() throws Exception {
        ValidateAuthorizationDraftResponseDto stub =
                ValidateAuthorizationDraftResponseDto.valid(
                        AuthorizationType.TIME_PERMISSION,
                        "Short absence request looks valid.");

        when(requestsService.validateAuthorizationDraft(any(ValidateAuthorizationDraftRequestDto.class), any()))
                .thenReturn(stub);

        String body = """
                {
                  "authorizationType": "TIME_PERMISSION",
                  "absenceDate": "%s",
                  "fromTime": "09:00",
                  "toTime": "11:00"
                }
                """.formatted(LocalDate.now().plusDays(1));

        mockMvc.perform(post(RequestApiRoutes.EMPLOYEE_AUTHORIZATIONS_VALIDATE_DRAFT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.authorizationType").value("TIME_PERMISSION"));

        verify(requestsService).validateAuthorizationDraft(any(), any());
    }

    // -------------------------------------------------------------------------
    // Blocked types — service returns valid=false
    // -------------------------------------------------------------------------

    @Test
    void businessTrip_returns200WithValidFalse() throws Exception {
        ValidateAuthorizationDraftResponseDto stub =
                ValidateAuthorizationDraftResponseDto.invalid(
                        "Authorization type BUSINESS_TRIP is no longer available for new requests.");

        when(requestsService.validateAuthorizationDraft(any(ValidateAuthorizationDraftRequestDto.class), any()))
                .thenReturn(stub);

        mockMvc.perform(post(RequestApiRoutes.EMPLOYEE_AUTHORIZATIONS_VALIDATE_DRAFT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"authorizationType\":\"BUSINESS_TRIP\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.errors[0]").value(
                        "Authorization type BUSINESS_TRIP is no longer available for new requests."));
    }

    @Test
    void training_returns200WithValidFalse() throws Exception {
        ValidateAuthorizationDraftResponseDto stub =
                ValidateAuthorizationDraftResponseDto.invalid(
                        "Authorization type TRAINING is no longer available for new requests.");

        when(requestsService.validateAuthorizationDraft(any(ValidateAuthorizationDraftRequestDto.class), any()))
                .thenReturn(stub);

        mockMvc.perform(post(RequestApiRoutes.EMPLOYEE_AUTHORIZATIONS_VALIDATE_DRAFT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"authorizationType\":\"TRAINING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.errors[0]").value(
                        "Authorization type TRAINING is no longer available for new requests."));
    }

    // -------------------------------------------------------------------------
    // Happy path — valid EQUIPMENT_REQUEST
    // -------------------------------------------------------------------------

    @Test
    void validEquipmentRequest_returns200WithValidTrue() throws Exception {
        ValidateAuthorizationDraftResponseDto stub =
                ValidateAuthorizationDraftResponseDto.valid(
                        AuthorizationType.EQUIPMENT_REQUEST,
                        "Equipment borrowing request looks valid.");

        when(requestsService.validateAuthorizationDraft(any(ValidateAuthorizationDraftRequestDto.class), any()))
                .thenReturn(stub);

        String body = """
                {
                  "authorizationType": "EQUIPMENT_REQUEST",
                  "equipmentType": "Laptop",
                  "reason": "Working from home on a deadline project",
                  "startDate": "%s",
                  "endDate": "%s"
                }
                """.formatted(LocalDate.now().plusDays(1), LocalDate.now().plusDays(7));

        mockMvc.perform(post(RequestApiRoutes.EMPLOYEE_AUTHORIZATIONS_VALIDATE_DRAFT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.authorizationType").value("EQUIPMENT_REQUEST"));
    }

    // -------------------------------------------------------------------------
    // Business-rule failure — service returns valid=false with errors[]
    // -------------------------------------------------------------------------

    @Test
    void invalidDraft_returns200WithValidFalseAndErrors() throws Exception {
        ValidateAuthorizationDraftResponseDto stub =
                ValidateAuthorizationDraftResponseDto.invalid(
                        AuthorizationType.TIME_PERMISSION,
                        List.of("Short absence requests require a date (absenceDate).",
                                "Short absence requests require fromTime."));

        when(requestsService.validateAuthorizationDraft(any(ValidateAuthorizationDraftRequestDto.class), any()))
                .thenReturn(stub);

        mockMvc.perform(post(RequestApiRoutes.EMPLOYEE_AUTHORIZATIONS_VALIDATE_DRAFT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"authorizationType\":\"TIME_PERMISSION\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    // -------------------------------------------------------------------------
    // Test infrastructure
    // -------------------------------------------------------------------------

    /**
     * Resolves any controller parameter annotated with {@link AuthenticationPrincipal}
     * whose declared type is {@link Jwt} to a stub Jwt with a non-empty
     * tokenValue and a "sub" claim. Used only in standalone MockMvc tests
     * where Spring Security is not active.
     *
     * <p>The stub claim values mirror the project's existing pattern from
     * {@code UserRoleInfoControllerTest.jwt(...)}: tokenValue="token",
     * header alg="none", claim sub="kc-test-user".</p>
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
            // Mirrors the existing project pattern from UserRoleInfoControllerTest.jwt(...)
            // — tokenValue="token", alg header, sub + preferred_username claims.
            return Jwt.withTokenValue("token")
                    .header("alg", "none")
                    .claim("sub", "kc-test-user")
                    .claim("preferred_username", "test-user")
                    .build();
        }
    }
}
