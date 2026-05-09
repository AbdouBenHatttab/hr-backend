package tn.isetbizerte.pfe.hrbackend.infrastructure.reporting;

import org.junit.jupiter.api.Test;
import tn.isetbizerte.pfe.hrbackend.common.enums.LoanType;
import tn.isetbizerte.pfe.hrbackend.modules.requests.entity.LoanRequest;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.Person;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class RequestReportServiceLoanPdfTest {

    @Test
    void generateLoanPdf_withoutRepaymentMonths_usesSafeFallback() {
        RequestReportService service = new RequestReportService();
        service.init();

        LoanRequest req = new LoanRequest();
        req.setId(1L);
        req.setLoanType(LoanType.PERSONAL_ADVANCE);
        req.setAmount(new BigDecimal("1200"));
        req.setRepaymentMonths(null);
        req.setReason("Need support");
        req.setRequestedAt(LocalDateTime.now());
        req.setProcessedAt(LocalDateTime.now());

        User user = new User();
        Person person = new Person();
        person.setFirstName("Amina");
        person.setLastName("Ben Ali");
        person.setEmail("amina@example.test");
        user.setPerson(person);
        req.setUser(user);

        byte[] pdf = service.generateLoanPdf(req);

        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(0);
    }
}
