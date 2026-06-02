package tn.isetbizerte.pfe.hrbackend.modules.requests.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.isetbizerte.pfe.hrbackend.common.enums.LeaveType;
import tn.isetbizerte.pfe.hrbackend.modules.employee.entity.EmployeeLeaveBalance;
import tn.isetbizerte.pfe.hrbackend.modules.employee.repository.EmployeeLeaveBalanceRepository;
import tn.isetbizerte.pfe.hrbackend.modules.requests.entity.DocumentRequest;

import java.time.LocalDate;

/**
 * Read-only access point for the leave balance shown on the Leave Balance Statement PDF.
 * Keeps the repository lookup in the service layer so report controllers stay repository-free.
 */
@Service
public class DocumentReportBalanceService {

    private final EmployeeLeaveBalanceRepository leaveBalanceRepository;

    public DocumentReportBalanceService(EmployeeLeaveBalanceRepository leaveBalanceRepository) {
        this.leaveBalanceRepository = leaveBalanceRepository;
    }

    /**
     * Read-only snapshot of the request employee's ANNUAL leave balance for the current year.
     * Returns null when unavailable; a missing/failed lookup must not crash PDF generation.
     */
    @Transactional(readOnly = true)
    public EmployeeLeaveBalance currentAnnualLeaveBalance(DocumentRequest req) {
        try {
            if (req == null || req.getUser() == null) return null;
            return leaveBalanceRepository
                    .findByUserAndLeaveTypeAndYear(req.getUser(), LeaveType.ANNUAL, LocalDate.now().getYear())
                    .orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
