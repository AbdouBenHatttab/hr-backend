package tn.isetbizerte.pfe.hrbackend.modules.employee.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.isetbizerte.pfe.hrbackend.common.enums.LeaveType;
import tn.isetbizerte.pfe.hrbackend.modules.employee.entity.EmployeeLeaveBalance;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeLeaveBalanceRepository extends JpaRepository<EmployeeLeaveBalance, Long> {
    Optional<EmployeeLeaveBalance> findByUserAndLeaveTypeAndYear(User user, LeaveType leaveType, Integer year);

    List<EmployeeLeaveBalance> findByUserAndYearOrderByLeaveTypeAsc(User user, Integer year);
}
