package tn.isetbizerte.pfe.hrbackend.modules.hr.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tn.isetbizerte.pfe.hrbackend.common.enums.TypeRole;
import tn.isetbizerte.pfe.hrbackend.common.exception.BadRequestException;
import tn.isetbizerte.pfe.hrbackend.infrastructure.kafka.producer.KafkaEventProducer;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.service.UserService;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class HRServiceAccountActivationTest {

    private UserService userService;
    private KeycloakAdminService keycloakAdminService;
    private HRService service;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        keycloakAdminService = mock(KeycloakAdminService.class);
        service = new HRService(userService, keycloakAdminService, mock(KafkaEventProducer.class));
    }

    @Test
    void deactivateUser_blocksHrManagerFromDeactivatingOwnAccount() {
        User hr = user(1L, "hr", TypeRole.HR_MANAGER, true);
        when(userService.findById(1L)).thenReturn(Optional.of(hr));

        assertThatThrownBy(() -> service.deactivateUser(1L, "hr"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("You cannot deactivate your own account.");

        verify(keycloakAdminService, never()).setUserEnabled(anyString(), anyBoolean());
        verify(userService, never()).saveUser(any());
    }

    @Test
    void deactivateAndReactivateUser_updatesKeycloakAndLocalActiveFlag() {
        User employee = user(2L, "employee", TypeRole.EMPLOYEE, true);
        when(userService.findById(2L)).thenReturn(Optional.of(employee));
        when(keycloakAdminService.setUserEnabled("kc-employee", false)).thenReturn(true);
        when(keycloakAdminService.setUserEnabled("kc-employee", true)).thenReturn(true);

        Map<String, Object> deactivated = service.deactivateUser(2L, "hr");

        assertThat(deactivated).containsEntry("active", false);
        assertThat(employee.getActive()).isFalse();
        verify(userService).saveUser(employee);

        Map<String, Object> activated = service.activateUser(2L, "hr");

        assertThat(activated).containsEntry("active", true);
        assertThat(employee.getActive()).isTrue();
        verify(keycloakAdminService).setUserEnabled("kc-employee", false);
        verify(keycloakAdminService).setUserEnabled("kc-employee", true);
        verify(userService, times(2)).saveUser(employee);
    }

    private User user(Long id, String username, TypeRole role, boolean active) {
        User user = new User("kc-" + username, username);
        user.setId(id);
        user.setRole(role);
        user.setActive(active);
        return user;
    }
}
