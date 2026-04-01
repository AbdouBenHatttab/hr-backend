package tn.isetbizerte.pfe.hrbackend.modules.report.service;

import org.springframework.stereotype.Service;
import tn.isetbizerte.pfe.hrbackend.common.enums.ApprovalDecision;
import tn.isetbizerte.pfe.hrbackend.common.enums.LeaveStatus;
import tn.isetbizerte.pfe.hrbackend.common.enums.TypeRole;
import tn.isetbizerte.pfe.hrbackend.common.exception.BadRequestException;
import tn.isetbizerte.pfe.hrbackend.common.exception.ResourceNotFoundException;
import tn.isetbizerte.pfe.hrbackend.common.exception.UnauthorizedException;
import tn.isetbizerte.pfe.hrbackend.infrastructure.reporting.LeaveReportService;
import tn.isetbizerte.pfe.hrbackend.modules.employee.entity.LeaveRequest;
import tn.isetbizerte.pfe.hrbackend.modules.employee.repository.LeaveRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.report.dto.LeaveApprovalDocument;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.UserRepository;

import java.util.Locale;

@Service
public class LeaveReportApplicationService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveReportService leaveReportService;
    private final UserRepository userRepository;

    public LeaveReportApplicationService(
            LeaveRequestRepository leaveRequestRepository,
            LeaveReportService leaveReportService,
            UserRepository userRepository
    ) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.leaveReportService = leaveReportService;
        this.userRepository = userRepository;
    }

    /**
     * Generates a leave approval PDF.
     * Security: employees can only download their own reports.
     * HR managers and team leaders can download any report.
     *
     * @param leaveId        the leave request ID
     * @param requesterKeycloakId the keycloak ID of the user making the request
     */
    public LeaveApprovalDocument generateLeaveApprovalDocument(Long leaveId, String requesterKeycloakId) {
        LeaveRequest leaveRequest = leaveRequestRepository.findByIdWithUserAndPerson(leaveId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found with id: " + leaveId));

        User requester = userRepository.findByKeycloakId(requesterKeycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        boolean isOwner = leaveRequest.getUser().getKeycloakId().equals(requesterKeycloakId);

        // EMPLOYEE: can only download their own PDF
        if (requester.getRole() == TypeRole.EMPLOYEE && !isOwner) {
            throw new UnauthorizedException("You are not authorized to download this document");
        }

        // TEAM_LEADER: can download their own PDF or their team members' PDFs
        if (requester.getRole() == TypeRole.TEAM_LEADER && !isOwner) {
            User employee = leaveRequest.getUser();
            boolean isTeamMember = employee.getTeam() != null
                    && employee.getTeam().getTeamLeader() != null
                    && employee.getTeam().getTeamLeader().getKeycloakId().equals(requesterKeycloakId);
            if (!isTeamMember) {
                throw new UnauthorizedException("You can only download PDFs for employees in your team.");
            }
        }

        // HR_MANAGER: can download any PDF — no restriction needed

        validateEligibleForPdfGeneration(leaveRequest);

        byte[] pdfBytes = leaveReportService.generateLeaveApprovalPdf(leaveRequest);
        String fileName = buildFileName(leaveRequest);
        return new LeaveApprovalDocument(pdfBytes, fileName);
    }

    private void validateEligibleForPdfGeneration(LeaveRequest leaveRequest) {
        boolean isApproved = leaveRequest.getStatus() == LeaveStatus.APPROVED
                && leaveRequest.getTeamLeaderDecision() == ApprovalDecision.APPROVED
                && leaveRequest.getHrDecision() == ApprovalDecision.APPROVED;

        if (!isApproved) {
            throw new BadRequestException("Leave approval PDF is available only when Team Leader and HR Manager approvals are both APPROVED");
        }
    }

    private String buildFileName(LeaveRequest leaveRequest) {
        String employeeName = leaveRequest.getEmployeeFullName()
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");

        return "leave_approval_" + employeeName + "_" + leaveRequest.getStartDate() + ".pdf";
    }
}

