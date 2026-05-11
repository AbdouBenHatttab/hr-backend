package tn.isetbizerte.pfe.hrbackend.modules.hr.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.isetbizerte.pfe.hrbackend.modules.hr.entity.HrManualEmailLog;

@Repository
public interface HrManualEmailLogRepository extends JpaRepository<HrManualEmailLog, Long> {
}
