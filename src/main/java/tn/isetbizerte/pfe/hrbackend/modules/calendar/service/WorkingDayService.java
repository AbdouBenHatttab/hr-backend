package tn.isetbizerte.pfe.hrbackend.modules.calendar.service;

import org.springframework.stereotype.Service;
import tn.isetbizerte.pfe.hrbackend.modules.calendar.repository.PublicHolidayRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;

@Service
public class WorkingDayService {
    private static final String TUNISIA = "TN";

    private final PublicHolidayRepository publicHolidayRepository;

    public WorkingDayService(PublicHolidayRepository publicHolidayRepository) {
        this.publicHolidayRepository = publicHolidayRepository;
    }

    public boolean isWorkingDay(LocalDate date) {
        if (date == null) return false;
        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        return !publicHolidayRepository.existsByCountryCodeAndHolidayDateAndActiveTrue(TUNISIA, date);
    }

    public int countWorkingDays(LocalDate start, LocalDate end) {
        if (start == null || end == null || start.isAfter(end)) {
            return 0;
        }
        int days = 0;
        LocalDate current = start;
        while (!current.isAfter(end)) {
            if (isWorkingDay(current)) {
                days++;
            }
            current = current.plusDays(1);
        }
        return days;
    }
}
