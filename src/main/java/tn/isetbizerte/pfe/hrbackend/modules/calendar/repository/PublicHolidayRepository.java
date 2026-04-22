package tn.isetbizerte.pfe.hrbackend.modules.calendar.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.isetbizerte.pfe.hrbackend.modules.calendar.entity.PublicHoliday;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface PublicHolidayRepository extends JpaRepository<PublicHoliday, Long> {
    boolean existsByCountryCodeAndHolidayDateAndActiveTrue(String countryCode, LocalDate holidayDate);

    Optional<PublicHoliday> findBySourceAndSourceKey(String source, String sourceKey);

    Optional<PublicHoliday> findByCountryCodeAndHolidayDateAndSource(String countryCode, LocalDate holidayDate, String source);
}
