package tn.isetbizerte.pfe.hrbackend.modules.employee.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tn.isetbizerte.pfe.hrbackend.common.enums.TypeRole;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.UserRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Service
public class LeaveAccrualService {
    private static final Logger log = LoggerFactory.getLogger(LeaveAccrualService.class);
    private static final Set<TypeRole> ACCRUAL_ROLES = Set.of(
            TypeRole.EMPLOYEE,
            TypeRole.TEAM_LEADER,
            TypeRole.HR_MANAGER
    );

    private final UserRepository userRepository;
    private final LeaveBalanceService leaveBalanceService;
    private final boolean accrueOnStartup;

    public LeaveAccrualService(UserRepository userRepository,
                               LeaveBalanceService leaveBalanceService,
                               @Value("${app.leave.accrual-on-startup:true}") boolean accrueOnStartup) {
        this.userRepository = userRepository;
        this.leaveBalanceService = leaveBalanceService;
        this.accrueOnStartup = accrueOnStartup;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void accrueOnStartup() {
        if (!accrueOnStartup) {
            log.info("Annual leave startup accrual is disabled");
            return;
        }
        accrueAnnualLeaveForCurrentYear();
    }

    @Scheduled(cron = "${app.leave.accrual-cron:0 15 1 1 * *}")
    public void scheduledMonthlyAccrual() {
        accrueAnnualLeaveForCurrentYear();
    }

    public int accrueAnnualLeaveForCurrentYear() {
        int year = LocalDate.now().getYear();
        List<User> activeUsers = userRepository.findByActive(true);
        int processed = 0;
        for (User user : activeUsers) {
            if (user.getRole() == null || !ACCRUAL_ROLES.contains(user.getRole())) {
                continue;
            }
            leaveBalanceService.accrueAnnualLeaveToDate(user, year);
            processed++;
        }
        log.info("Processed annual leave accrual for {} active employee user(s) in {}", processed, year);
        return processed;
    }
}
