package tn.isetbizerte.pfe.hrbackend.modules.jobtitle.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.isetbizerte.pfe.hrbackend.modules.jobtitle.entity.JobTitle;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobTitleRepository extends JpaRepository<JobTitle, Long> {

    Optional<JobTitle> findByNameIgnoreCase(String name);

    List<JobTitle> findAllByOrderByNameAsc();

    List<JobTitle> findByActiveTrueOrderByNameAsc();
}
