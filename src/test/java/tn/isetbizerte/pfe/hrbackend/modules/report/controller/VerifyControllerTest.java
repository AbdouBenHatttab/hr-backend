package tn.isetbizerte.pfe.hrbackend.modules.report.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import tn.isetbizerte.pfe.hrbackend.common.enums.LeaveStatus;
import tn.isetbizerte.pfe.hrbackend.modules.employee.entity.LeaveRequest;
import tn.isetbizerte.pfe.hrbackend.modules.employee.repository.LeaveRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.AuthorizationRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.DocumentRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.LoanRequestRepository;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VerifyControllerTest {

    private final LeaveRequestRepository leaveRequestRepository = mock(LeaveRequestRepository.class);
    private final DocumentRequestRepository documentRequestRepository = mock(DocumentRequestRepository.class);
    private final LoanRequestRepository loanRequestRepository = mock(LoanRequestRepository.class);
    private final AuthorizationRequestRepository authorizationRequestRepository = mock(AuthorizationRequestRepository.class);
    private final VerifyController controller = new VerifyController(
            leaveRequestRepository,
            documentRequestRepository,
            loanRequestRepository,
            authorizationRequestRepository
    );

    @Test
    void verifyDocument_returnsSafePublicResponseWithoutPersonalData() {
        LeaveRequest leave = new LeaveRequest();
        leave.setStatus(LeaveStatus.APPROVED);
        leave.setApprovalDate(LocalDate.of(2026, 4, 1));
        when(leaveRequestRepository.findByVerificationToken("valid-token")).thenReturn(Optional.of(leave));

        ResponseEntity<Map<String, Object>> response = controller.verifyDocument("valid-token");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody())
                .containsEntry("valid", true)
                .containsEntry("documentType", "LEAVE")
                .containsEntry("status", "APPROVED")
                .containsEntry("issuedOn", "2026-04-01")
                .containsEntry("issuer", "ArabSoft Human Resources Management System");
        assertThat(response.getBody()).doesNotContainKeys(
                "employeeName",
                "employeeEmail",
                "reason",
                "salary",
                "userId"
        );
    }

    @Test
    void verifyDocument_returnsSafe404ForUnknownToken() {
        when(leaveRequestRepository.findByVerificationToken("missing")).thenReturn(Optional.empty());
        when(documentRequestRepository.findByVerificationToken("missing")).thenReturn(Optional.empty());
        when(loanRequestRepository.findByVerificationToken("missing")).thenReturn(Optional.empty());
        when(authorizationRequestRepository.findByVerificationToken("missing")).thenReturn(Optional.empty());

        ResponseEntity<Map<String, Object>> response = controller.verifyDocument("missing");

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody())
                .containsEntry("valid", false)
                .containsEntry("message", "Document not found or token is invalid.");
    }
}
