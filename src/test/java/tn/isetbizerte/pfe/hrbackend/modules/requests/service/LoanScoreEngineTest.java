package tn.isetbizerte.pfe.hrbackend.modules.requests.service;

import org.junit.jupiter.api.Test;
import tn.isetbizerte.pfe.hrbackend.common.enums.LoanType;
import tn.isetbizerte.pfe.hrbackend.modules.requests.entity.LoanRequest;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.LoanRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.Person;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoanScoreEngineTest {

    @Test
    void evaluate_withoutRepaymentMonths_doesNotInventOneMonth() {
        LoanRequestRepository loanRepo = mock(LoanRequestRepository.class);
        when(loanRepo.findByUserOrderByRequestedAtDesc(any(User.class))).thenReturn(java.util.List.of());

        LoanScoreEngine engine = new LoanScoreEngine(loanRepo);

        User user = new User();
        Person person = new Person();
        person.setSalary(new BigDecimal("3000"));
        person.setHireDate(LocalDate.now().minusMonths(24));
        user.setPerson(person);

        LoanRequest loan = new LoanRequest();
        loan.setUser(user);
        loan.setLoanType(LoanType.PERSONAL_ADVANCE);
        loan.setAmount(new BigDecimal("1200"));
        loan.setRepaymentMonths(null);

        engine.evaluate(loan);

        assertThat(loan.getRepaymentMonths()).isNull();
        assertThat(loan.getMonthlyInstallment()).isNull();
        assertThat(loan.getSystemRecommendation()).isNotNull();
    }

    @Test
    void evaluate_withRepaymentMonths_usesProvidedValue() {
        LoanRequestRepository loanRepo = mock(LoanRequestRepository.class);
        when(loanRepo.findByUserOrderByRequestedAtDesc(any(User.class))).thenReturn(java.util.List.of());

        LoanScoreEngine engine = new LoanScoreEngine(loanRepo);

        User user = new User();
        Person person = new Person();
        person.setSalary(new BigDecimal("3000"));
        person.setHireDate(LocalDate.now().minusMonths(24));
        user.setPerson(person);

        LoanRequest loan = new LoanRequest();
        loan.setUser(user);
        loan.setLoanType(LoanType.PERSONAL_ADVANCE);
        loan.setAmount(new BigDecimal("1200"));
        loan.setRepaymentMonths(12);

        engine.evaluate(loan);

        assertThat(loan.getRepaymentMonths()).isEqualTo(12);
        assertThat(loan.getMonthlyInstallment()).isEqualByComparingTo("100.00");
    }
}
