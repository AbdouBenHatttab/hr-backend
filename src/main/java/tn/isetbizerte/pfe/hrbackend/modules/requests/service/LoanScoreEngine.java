package tn.isetbizerte.pfe.hrbackend.modules.requests.service;

import org.springframework.stereotype.Service;
import tn.isetbizerte.pfe.hrbackend.common.enums.RequestStatus;
import tn.isetbizerte.pfe.hrbackend.modules.requests.entity.LoanRequest;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.LoanRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.Person;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Loan Scoring Engine
 * ──────────────────────────────────────────────────────────────────
 * After hard rules pass, calculates:
 *   - monthlyInstallment     = amount / repaymentMonths
 *   - risk_score             (0–100, higher = less risky)
 *   - system_recommendation  APPROVE / REVIEW / REJECT
 *   - decision_reason        human-readable explanation
 *   - meeting_required       true for REVIEW
 *
 * Risk formula:
 *   score = 100
 *         - loanRatioPenalty    (0–40)  amount vs max allowed
 *         - deductionPenalty    (0–30)  monthly deduction vs salary
 *         + stabilityBonus      (0–20)  seniority
 *         - historyPenalty      (0–10)  previous loans
 */
@Service
public class LoanScoreEngine {

    private final LoanRequestRepository loanRepo;

    public LoanScoreEngine(LoanRequestRepository loanRepo) {
        this.loanRepo = loanRepo;
    }

    public void evaluate(LoanRequest loan) {
        User   user   = loan.getUser();
        Person person = user.getPerson();

        BigDecimal salary      = person.getSalary();
        BigDecimal amount      = loan.getAmount();
        int        months      = loan.getRepaymentMonths();
        BigDecimal existingDed = person.getCurrentMonthlyDeductions() != null
                ? person.getCurrentMonthlyDeductions()
                : BigDecimal.ZERO;

        // Monthly installment for this loan
        BigDecimal installment = amount.divide(
                BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
        loan.setMonthlyInstallment(installment);

        int score = 100;
        List<String> reasons = new ArrayList<>();

        // ── Loan ratio penalty (0–40) ──────────────────────────────────────
        // How much of the 3× max are they requesting?
        BigDecimal maxAllowed   = salary.multiply(new BigDecimal("3"));
        double     loanRatio    = amount.divide(maxAllowed, 4, RoundingMode.HALF_UP).doubleValue();
        int        loanPenalty  = 0;
        if (loanRatio > 0.9) {
            loanPenalty = 40;
            reasons.add(String.format("Requesting %.0f%% of maximum allowed amount (high). ",
                    loanRatio * 100));
        } else if (loanRatio > 0.7) {
            loanPenalty = 25;
            reasons.add(String.format("Requesting %.0f%% of maximum allowed amount (medium). ",
                    loanRatio * 100));
        } else if (loanRatio > 0.5) {
            loanPenalty = 10;
            reasons.add(String.format("Requesting %.0f%% of maximum allowed amount. ",
                    loanRatio * 100));
        }
        score -= loanPenalty;

        // ── Deduction penalty (0–30) ───────────────────────────────────────
        // Total monthly deductions after this loan vs salary
        BigDecimal totalDeductions   = existingDed.add(installment);
        double     deductionRatio    = totalDeductions.divide(salary, 4, RoundingMode.HALF_UP).doubleValue();
        int        deductionPenalty  = 0;
        if (deductionRatio > 0.40) {
            deductionPenalty = 30;
            reasons.add(String.format(
                "Total deductions would be %.0f%% of salary (exceeds 40%% threshold). ",
                deductionRatio * 100));
        } else if (deductionRatio > 0.30) {
            deductionPenalty = 20;
            reasons.add(String.format(
                "Total deductions would be %.0f%% of salary (above 30%% warning threshold). ",
                deductionRatio * 100));
        } else if (deductionRatio > 0.20) {
            deductionPenalty = 8;
            reasons.add(String.format(
                "Deductions at %.0f%% of salary (manageable). ", deductionRatio * 100));
        }
        score -= deductionPenalty;

        // ── Stability bonus (0–20) ─────────────────────────────────────────
        int stabilityBonus = 0;
        if (person.getHireDate() != null) {
            long monthsEmployed = ChronoUnit.MONTHS.between(person.getHireDate(), LocalDate.now());
            if (monthsEmployed >= 36) {
                stabilityBonus = 20;
                reasons.add(String.format("Long tenure: %d months employed (+20 stability bonus). ",
                        monthsEmployed));
            } else if (monthsEmployed >= 24) {
                stabilityBonus = 15;
                reasons.add(String.format("Good tenure: %d months (+15 stability bonus). ",
                        monthsEmployed));
            } else if (monthsEmployed >= 12) {
                stabilityBonus = 10;
                reasons.add(String.format("Adequate tenure: %d months (+10 stability bonus). ",
                        monthsEmployed));
            } else {
                stabilityBonus = 5;
                reasons.add(String.format("Recent hire: %d months (limited stability bonus). ",
                        monthsEmployed));
            }
        }
        score = Math.min(100, score + stabilityBonus);

        // ── History penalty (0–10) ─────────────────────────────────────────
        long pastLoans = loanRepo.findByUserOrderByRequestedAtDesc(user).stream()
                .filter(l -> l.getStatus() == RequestStatus.APPROVED ||
                             l.getStatus() == RequestStatus.REJECTED)
                .count();
        int historyPenalty = 0;
        if (pastLoans >= 3) {
            historyPenalty = 10;
            reasons.add(String.format("%d previous loan(s) on record. ", pastLoans));
        } else if (pastLoans >= 2) {
            historyPenalty = 5;
            reasons.add(String.format("%d previous loan(s) on record. ", pastLoans));
        }
        score -= historyPenalty;
        score = Math.max(0, score);

        // ── Recommendation ─────────────────────────────────────────────────
        String recommendation;
        boolean meetingRequired;
        if (deductionRatio > 0.40) {
            // Hard threshold — deductions too high
            recommendation  = "REJECT";
            meetingRequired = false;
            reasons.add("AUTO-REJECTED: monthly deductions would exceed 40% of salary. ");
        } else if (score >= 65) {
            recommendation  = "APPROVE";
            meetingRequired = false;
            if (reasons.stream().noneMatch(r -> r.contains("bonus")))
                reasons.add("Loan meets all criteria with acceptable risk. ");
        } else if (score >= 35) {
            recommendation  = "REVIEW";
            meetingRequired = true;
            reasons.add("HR meeting required before final approval. ");
        } else {
            recommendation  = "REJECT";
            meetingRequired = false;
            reasons.add("Risk score too low — loan not recommended. ");
        }

        loan.setRiskScore(score);
        loan.setSystemRecommendation(recommendation);
        loan.setDecisionReason(String.join("", reasons).trim());
        loan.setMeetingRequired(meetingRequired);
    }
}
