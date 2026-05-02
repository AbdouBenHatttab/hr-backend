package tn.isetbizerte.pfe.hrbackend.modules.hr.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tn.isetbizerte.pfe.hrbackend.common.enums.ApprovalDecision;
import tn.isetbizerte.pfe.hrbackend.common.enums.DocumentType;
import tn.isetbizerte.pfe.hrbackend.common.enums.LeaveStatus;
import tn.isetbizerte.pfe.hrbackend.common.enums.RequestStatus;
import tn.isetbizerte.pfe.hrbackend.modules.employee.repository.LeaveRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.hr.dto.RequestActionSummary;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.AuthorizationRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.DocumentRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.LoanRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.UserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardRequestSummaryServiceTest {

    private LeaveRequestRepository leaveRequestRepository;
    private DocumentRequestRepository documentRequestRepository;
    private LoanRequestRepository loanRequestRepository;
    private AuthorizationRequestRepository authorizationRequestRepository;
    private UserRepository userRepository;
    private DashboardRequestSummaryService service;

    @BeforeEach
    void setUp() {
        leaveRequestRepository = mock(LeaveRequestRepository.class);
        documentRequestRepository = mock(DocumentRequestRepository.class);
        loanRequestRepository = mock(LoanRequestRepository.class);
        authorizationRequestRepository = mock(AuthorizationRequestRepository.class);
        userRepository = mock(UserRepository.class);
        service = new DashboardRequestSummaryService(
                leaveRequestRepository,
                documentRequestRepository,
                loanRequestRepository,
                authorizationRequestRepository,
                userRepository
        );
    }

    @Test
    void hrSummary_countsAllHrActionCategoriesAndTotal() {
        when(leaveRequestRepository.countByStatusAndTeamLeaderDecisionAndHrDecision(
                LeaveStatus.PENDING,
                ApprovalDecision.APPROVED,
                ApprovalDecision.PENDING
        )).thenReturn(2L);
        when(documentRequestRepository.countByStatus(RequestStatus.PENDING)).thenReturn(3L);
        when(documentRequestRepository.countByStatusAndDocumentTypeNotAndAttachmentStoragePathIsNull(
                RequestStatus.APPROVED,
                DocumentType.LEAVE_BALANCE_STATEMENT
        )).thenReturn(4L);
        when(documentRequestRepository.countByStatusAndDocumentTypeNotAndAttachmentStoragePath(
                RequestStatus.APPROVED,
                DocumentType.LEAVE_BALANCE_STATEMENT,
                ""
        )).thenReturn(1L);
        when(loanRequestRepository.countByStatus(RequestStatus.PENDING)).thenReturn(5L);
        when(loanRequestRepository.countByStatusAndAttachmentStoragePathIsNull(RequestStatus.APPROVED)).thenReturn(6L);
        when(loanRequestRepository.countByStatusAndAttachmentStoragePath(RequestStatus.APPROVED, "")).thenReturn(1L);
        when(authorizationRequestRepository.countByStatus(RequestStatus.PENDING)).thenReturn(7L);

        RequestActionSummary summary = service.getHrRequestActionSummary();

        assertThat(summary.leavesPending()).isEqualTo(2);
        assertThat(summary.documentsPending()).isEqualTo(3);
        assertThat(summary.documentsAwaitingFile()).isEqualTo(5);
        assertThat(summary.loansPending()).isEqualTo(5);
        assertThat(summary.loansAwaitingFile()).isEqualTo(7);
        assertThat(summary.authorizationsPending()).isEqualTo(7);
        assertThat(summary.total()).isEqualTo(29);
    }

    @Test
    void hrSummary_onlyUsesHrPendingLeaveCriteria() {
        service.getHrRequestActionSummary();

        verify(leaveRequestRepository).countByStatusAndTeamLeaderDecisionAndHrDecision(
                LeaveStatus.PENDING,
                ApprovalDecision.APPROVED,
                ApprovalDecision.PENDING
        );
        verify(leaveRequestRepository, never()).countByStatusAndTeamLeaderDecisionAndHrDecision(
                LeaveStatus.PENDING,
                ApprovalDecision.PENDING,
                ApprovalDecision.PENDING
        );
    }

    @Test
    void employeeSummary_countsOnlyAuthenticatedUsersOpenRequestsAndTotal() {
        User user = new User("kc-employee", "employee");
        user.setId(12L);
        when(userRepository.findByKeycloakId("kc-employee")).thenReturn(Optional.of(user));
        when(leaveRequestRepository.countByUserAndStatus(user, LeaveStatus.PENDING)).thenReturn(2L);
        when(documentRequestRepository.countByUserAndStatus(user, RequestStatus.PENDING)).thenReturn(3L);
        when(documentRequestRepository.countByUserAndStatusAndDocumentTypeNotAndAttachmentStoragePathIsNull(
                user,
                RequestStatus.APPROVED,
                DocumentType.LEAVE_BALANCE_STATEMENT
        )).thenReturn(4L);
        when(documentRequestRepository.countByUserAndStatusAndDocumentTypeNotAndAttachmentStoragePath(
                user,
                RequestStatus.APPROVED,
                DocumentType.LEAVE_BALANCE_STATEMENT,
                ""
        )).thenReturn(1L);
        when(loanRequestRepository.countByUserAndStatus(user, RequestStatus.PENDING)).thenReturn(5L);
        when(loanRequestRepository.countByUserAndStatusAndAttachmentStoragePathIsNull(user, RequestStatus.APPROVED)).thenReturn(6L);
        when(loanRequestRepository.countByUserAndStatusAndAttachmentStoragePath(user, RequestStatus.APPROVED, "")).thenReturn(1L);
        when(authorizationRequestRepository.countByUserAndStatus(user, RequestStatus.PENDING)).thenReturn(7L);

        RequestActionSummary summary = service.getEmployeeOpenRequestsSummary("kc-employee");

        assertThat(summary.leavesPending()).isEqualTo(2);
        assertThat(summary.documentsPending()).isEqualTo(3);
        assertThat(summary.documentsAwaitingFile()).isEqualTo(5);
        assertThat(summary.loansPending()).isEqualTo(5);
        assertThat(summary.loansAwaitingFile()).isEqualTo(7);
        assertThat(summary.authorizationsPending()).isEqualTo(7);
        assertThat(summary.total()).isEqualTo(29);
        verify(userRepository).findByKeycloakId("kc-employee");
    }

    @Test
    void employeeSummary_doesNotCountClosedRequestStatuses() {
        User user = new User("kc-employee", "employee");
        when(userRepository.findByKeycloakId("kc-employee")).thenReturn(Optional.of(user));

        service.getEmployeeOpenRequestsSummary("kc-employee");

        verify(documentRequestRepository, never()).countByUserAndStatus(user, RequestStatus.REJECTED);
        verify(documentRequestRepository, never()).countByUserAndStatus(user, RequestStatus.CANCELLED_BY_EMPLOYEE);
        verify(loanRequestRepository, never()).countByUserAndStatus(user, RequestStatus.REJECTED);
        verify(loanRequestRepository, never()).countByUserAndStatus(user, RequestStatus.CANCELLED_BY_EMPLOYEE);
        verify(authorizationRequestRepository, never()).countByUserAndStatus(user, RequestStatus.REJECTED);
        verify(authorizationRequestRepository, never()).countByUserAndStatus(user, RequestStatus.CANCELLED_BY_EMPLOYEE);
    }
}
