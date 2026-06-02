package tn.isetbizerte.pfe.hrbackend.infrastructure.reporting;

import org.junit.jupiter.api.Test;
import tn.isetbizerte.pfe.hrbackend.common.enums.DocumentType;
import tn.isetbizerte.pfe.hrbackend.common.enums.LeaveType;
import tn.isetbizerte.pfe.hrbackend.common.enums.RequestStatus;
import tn.isetbizerte.pfe.hrbackend.modules.employee.entity.EmployeeLeaveBalance;
import tn.isetbizerte.pfe.hrbackend.modules.requests.entity.DocumentRequest;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.Person;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class RequestReportServiceDocumentPdfTest {

    private static DocumentRequest approvedLeaveBalanceStatement() {
        User user = new User();
        Person person = new Person();
        person.setFirstName("Sami");
        person.setLastName("Trabelsi");
        person.setEmail("sami@example.test");
        user.setPerson(person);

        DocumentRequest req = new DocumentRequest();
        req.setId(42L);
        req.setDocumentType(DocumentType.LEAVE_BALANCE_STATEMENT);
        req.setStatus(RequestStatus.APPROVED);
        req.setUser(user);
        req.setRequestedAt(LocalDateTime.now().minusDays(1));
        req.setProcessedAt(LocalDateTime.now());
        return req;
    }

    @Test
    void generateDocumentPdf_withBalance_rendersLeaveBalanceSummary() {
        RequestReportService service = new RequestReportService();
        service.init();

        EmployeeLeaveBalance balance = new EmployeeLeaveBalance();
        balance.setLeaveType(LeaveType.ANNUAL);
        balance.setYear(LocalDate.now().getYear());
        balance.setAllocatedDays(new BigDecimal("30.00"));
        balance.setCarryForwardDays(new BigDecimal("5.00"));
        balance.setUsedDays(new BigDecimal("12.50"));
        balance.setReservedDays(new BigDecimal("2.00"));

        byte[] pdf = service.generateDocumentPdf(approvedLeaveBalanceStatement(), balance);

        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(0);
    }

    @Test
    void generateDocumentPdf_withoutBalance_fallsBackWithoutCrashing() {
        RequestReportService service = new RequestReportService();
        service.init();

        byte[] pdf = service.generateDocumentPdf(approvedLeaveBalanceStatement(), null);

        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(0);
    }

    @Test
    void generateDocumentPdf_singleArgOverload_stillWorks() {
        RequestReportService service = new RequestReportService();
        service.init();

        byte[] pdf = service.generateDocumentPdf(approvedLeaveBalanceStatement());

        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(0);
    }
}
