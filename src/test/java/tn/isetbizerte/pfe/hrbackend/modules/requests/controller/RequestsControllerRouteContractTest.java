package tn.isetbizerte.pfe.hrbackend.modules.requests.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestsControllerRouteContractTest {

    @Test
    void employeeRequestRoutes_arePresentOnController() {
        assertHasGetMapping(RequestApiRoutes.EMPLOYEE_DOCUMENTS);
        assertHasPostMapping(RequestApiRoutes.EMPLOYEE_DOCUMENTS);
        assertHasPostMapping(RequestApiRoutes.EMPLOYEE_DOCUMENTS_CANCEL);
        assertHasGetMapping(RequestApiRoutes.EMPLOYEE_DOCUMENTS_ATTACHMENT);
        assertHasGetMapping(RequestApiRoutes.EMPLOYEE_DOCUMENTS_MANAGED);
        assertHasGetMapping(RequestApiRoutes.EMPLOYEE_DOCUMENTS_MANAGED_DOWNLOAD);

        assertHasGetMapping(RequestApiRoutes.EMPLOYEE_LOANS);
        assertHasPostMapping(RequestApiRoutes.EMPLOYEE_LOANS);
        assertHasPostMapping(RequestApiRoutes.EMPLOYEE_LOANS_CANCEL);
        assertHasGetMapping(RequestApiRoutes.EMPLOYEE_LOANS_ELIGIBILITY);
        assertHasGetMapping(RequestApiRoutes.EMPLOYEE_LOANS_ATTACHMENT);

        assertHasGetMapping(RequestApiRoutes.EMPLOYEE_AUTHORIZATIONS);
        assertHasPostMapping(RequestApiRoutes.EMPLOYEE_AUTHORIZATIONS);
        assertHasPostMapping(RequestApiRoutes.EMPLOYEE_AUTHORIZATIONS_CANCEL);
    }

    private void assertHasGetMapping(String expectedPath) {
        assertTrue(hasMapping(expectedPath, GetMapping.class), () -> "Missing @GetMapping for " + expectedPath);
    }

    private void assertHasPostMapping(String expectedPath) {
        assertTrue(hasMapping(expectedPath, PostMapping.class), () -> "Missing @PostMapping for " + expectedPath);
    }

    private boolean hasMapping(String expectedPath, Class<?> annotationType) {
        return Arrays.stream(RequestsController.class.getDeclaredMethods())
                .anyMatch(method -> mappingMatches(method, expectedPath, annotationType));
    }

    private boolean mappingMatches(Method method, String expectedPath, Class<?> annotationType) {
        if (annotationType == GetMapping.class) {
            GetMapping mapping = method.getAnnotation(GetMapping.class);
            return mapping != null && Arrays.asList(mapping.value()).contains(expectedPath);
        }

        if (annotationType == PostMapping.class) {
            PostMapping mapping = method.getAnnotation(PostMapping.class);
            return mapping != null && Arrays.asList(mapping.value()).contains(expectedPath);
        }

        return false;
    }
}
