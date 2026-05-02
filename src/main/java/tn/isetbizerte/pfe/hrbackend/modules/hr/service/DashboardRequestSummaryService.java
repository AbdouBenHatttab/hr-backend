package tn.isetbizerte.pfe.hrbackend.modules.hr.service;

import org.springframework.stereotype.Service;
import tn.isetbizerte.pfe.hrbackend.common.enums.ApprovalDecision;
import tn.isetbizerte.pfe.hrbackend.common.enums.DocumentType;
import tn.isetbizerte.pfe.hrbackend.common.enums.LeaveStatus;
import tn.isetbizerte.pfe.hrbackend.common.enums.RequestStatus;
import tn.isetbizerte.pfe.hrbackend.common.exception.ResourceNotFoundException;
import tn.isetbizerte.pfe.hrbackend.modules.employee.repository.LeaveRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.hr.dto.RequestActionSummary;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.AuthorizationRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.DocumentRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.LoanRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.UserRepository;

@Service
public class DashboardRequestSummaryService {

    private static final String BLANK_ATTACHMENT_PATH = "";

    private final LeaveRequestRepository leaveRequestRepository;
    private final DocumentRequestRepository documentRequestRepository;
    private final LoanRequestRepository loanRequestRepository;
    private final AuthorizationRequestRepository authorizationRequestRepository;
    private final UserRepository userRepository;

    public DashboardRequestSummaryService(
            LeaveRequestRepository leaveRequestRepository,
            DocumentRequestRepository documentRequestRepository,
            LoanRequestRepository loanRequestRepository,
            AuthorizationRequestRepository authorizationRequestRepository,
            UserRepository userRepository
    ) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.documentRequestRepository = documentRequestRepository;
        this.loanRequestRepository = loanRequestRepository;
        this.authorizationRequestRepository = authorizationRequestRepository;
        this.userRepository = userRepository;
    }

    public RequestActionSummary getHrRequestActionSummary() {
        long leavesPending = leaveRequestRepository.countByStatusAndTeamLeaderDecisionAndHrDecision(
                LeaveStatus.PENDING,
                ApprovalDecision.APPROVED,
                ApprovalDecision.PENDING
        );
        long documentsPending = documentRequestRepository.countByStatus(RequestStatus.PENDING);
        long documentsAwaitingFile = countDocumentsAwaitingFile();
        long loansPending = loanRequestRepository.countByStatus(RequestStatus.PENDING);
        long loansAwaitingFile = countLoansAwaitingFile();
        long authorizationsPending = authorizationRequestRepository.countByStatus(RequestStatus.PENDING);

        return RequestActionSummary.of(
                leavesPending,
                documentsPending,
                documentsAwaitingFile,
                loansPending,
                loansAwaitingFile,
                authorizationsPending
        );
    }

    public RequestActionSummary getEmployeeOpenRequestsSummary(String keycloakId) {
        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("Requester user not found"));

        long leavesPending = leaveRequestRepository.countByUserAndStatus(user, LeaveStatus.PENDING);
        long documentsPending = documentRequestRepository.countByUserAndStatus(user, RequestStatus.PENDING);
        long documentsAwaitingFile = countDocumentsAwaitingFile(user);
        long loansPending = loanRequestRepository.countByUserAndStatus(user, RequestStatus.PENDING);
        long loansAwaitingFile = countLoansAwaitingFile(user);
        long authorizationsPending = authorizationRequestRepository.countByUserAndStatus(user, RequestStatus.PENDING);

        return RequestActionSummary.of(
                leavesPending,
                documentsPending,
                documentsAwaitingFile,
                loansPending,
                loansAwaitingFile,
                authorizationsPending
        );
    }

    private long countDocumentsAwaitingFile() {
        return documentRequestRepository.countByStatusAndDocumentTypeNotAndAttachmentStoragePathIsNull(
                RequestStatus.APPROVED,
                DocumentType.LEAVE_BALANCE_STATEMENT
        ) + documentRequestRepository.countByStatusAndDocumentTypeNotAndAttachmentStoragePath(
                RequestStatus.APPROVED,
                DocumentType.LEAVE_BALANCE_STATEMENT,
                BLANK_ATTACHMENT_PATH
        );
    }

    private long countDocumentsAwaitingFile(User user) {
        return documentRequestRepository.countByUserAndStatusAndDocumentTypeNotAndAttachmentStoragePathIsNull(
                user,
                RequestStatus.APPROVED,
                DocumentType.LEAVE_BALANCE_STATEMENT
        ) + documentRequestRepository.countByUserAndStatusAndDocumentTypeNotAndAttachmentStoragePath(
                user,
                RequestStatus.APPROVED,
                DocumentType.LEAVE_BALANCE_STATEMENT,
                BLANK_ATTACHMENT_PATH
        );
    }

    private long countLoansAwaitingFile() {
        return loanRequestRepository.countByStatusAndAttachmentStoragePathIsNull(RequestStatus.APPROVED)
                + loanRequestRepository.countByStatusAndAttachmentStoragePath(RequestStatus.APPROVED, BLANK_ATTACHMENT_PATH);
    }

    private long countLoansAwaitingFile(User user) {
        return loanRequestRepository.countByUserAndStatusAndAttachmentStoragePathIsNull(user, RequestStatus.APPROVED)
                + loanRequestRepository.countByUserAndStatusAndAttachmentStoragePath(user, RequestStatus.APPROVED, BLANK_ATTACHMENT_PATH);
    }
}
