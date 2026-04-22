package tn.isetbizerte.pfe.hrbackend.modules.employee.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.isetbizerte.pfe.hrbackend.common.enums.LeaveType;
import tn.isetbizerte.pfe.hrbackend.modules.employee.entity.LeavePolicy;

import java.util.List;
import java.util.Optional;

@Repository
public interface LeavePolicyRepository extends JpaRepository<LeavePolicy, Long> {
    Optional<LeavePolicy> findByLeaveType(LeaveType leaveType);

    List<LeavePolicy> findByActiveTrueOrderByLeaveTypeAsc();
}
