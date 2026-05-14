package tn.isetbizerte.pfe.hrbackend.modules.hr.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import tn.isetbizerte.pfe.hrbackend.modules.hr.dto.HrReportExportRequest;
import tn.isetbizerte.pfe.hrbackend.modules.hr.service.HrReportExportService;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HRReportExportControllerTest {

    private HrReportExportService reportExportService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        reportExportService = mock(HrReportExportService.class);
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        mockMvc = MockMvcBuilders
                .standaloneSetup(new HRReportExportController(reportExportService))
                .setMessageConverters(new ByteArrayHttpMessageConverter(), new MappingJackson2HttpMessageConverter(objectMapper))
                .setCustomArgumentResolvers(new StubJwtAuthenticationPrincipalResolver())
                .build();
    }

    @Test
    void exportExcelReturnsExcelAttachmentHeaders() throws Exception {
        when(reportExportService.exportExcel(any(), any())).thenReturn(new byte[]{1, 2, 3});

        mockMvc.perform(post("/api/hr/reports/export/excel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "ArabSoft HR Report",
                                  "sourceTypes": ["LEAVE", "DOCUMENT"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("arabsoft-hr-report-")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".xlsx")));
    }

    @Test
    void exportExcelEndpointIsRestrictedToHrManager() throws Exception {
        Method method = HRReportExportController.class.getMethod("exportExcel", HrReportExportRequest.class, Jwt.class);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo("hasRole('HR_MANAGER')");
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
