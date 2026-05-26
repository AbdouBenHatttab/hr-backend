package tn.isetbizerte.pfe.hrbackend.modules.task.controller;

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
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import tn.isetbizerte.pfe.hrbackend.common.exception.GlobalExceptionHandler;
import tn.isetbizerte.pfe.hrbackend.modules.task.service.TaskService;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TaskControllerPreviewValidationTest {

    private TaskService taskService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        taskService = mock(TaskService.class);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        mockMvc = MockMvcBuilders
                .standaloneSetup(new TaskController(taskService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setCustomArgumentResolvers(new StubJwtAuthenticationPrincipalResolver())
                .build();
    }

    @Test
    void previewTaskAssignment_allowsEmptyTitleWhenDatesAndAssigneesAreValid() throws Exception {
        when(taskService.previewTaskAssignment(anyString(), anyLong(), any()))
                .thenReturn(Map.of("canCreate", true));

        mockMvc.perform(post("/api/leader/projects/500/tasks/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "task": {
                                    "title": "",
                                    "priority": "MEDIUM",
                                    "startDate": "2026-06-12",
                                    "dueDate": "2026-06-16",
                                    "assignmentMode": "ONE",
                                    "assigneeId": 20,
                                    "assigneeIds": []
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.canCreate").value(true));

        verify(taskService).previewTaskAssignment(anyString(), anyLong(), any());
    }

    @Test
    void createTask_stillRequiresTitle() throws Exception {
        mockMvc.perform(post("/api/leader/projects/500/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "",
                                  "priority": "MEDIUM",
                                  "startDate": "2026-06-12",
                                  "dueDate": "2026-06-16",
                                  "assignmentMode": "ONE",
                                  "assigneeId": 20,
                                  "assigneeIds": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("title: Task title is required"));

        verifyNoInteractions(taskService);
    }

    private static final class StubJwtAuthenticationPrincipalResolver implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
                    && Jwt.class.isAssignableFrom(parameter.getParameterType());
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                      NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
            return Jwt.withTokenValue("test-token")
                    .header("alg", "none")
                    .claim("sub", "kc-test-user")
                    .claim("preferred_username", "kc-test-user")
                    .build();
        }
    }
}
