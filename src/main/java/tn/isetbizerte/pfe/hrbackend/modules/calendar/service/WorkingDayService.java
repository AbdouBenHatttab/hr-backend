package tn.isetbizerte.pfe.hrbackend.modules.calendar.service;

import org.springframework.stereotype.Service;
import tn.isetbizerte.pfe.hrbackend.modules.calendar.repository.PublicHolidayRepository;
import tn.isetbizerte.pfe.hrbackend.modules.calendar.dto.WorkingDaysEstimateDto;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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
        return !isPublicHoliday(date);
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

    public WorkingDaysEstimateDto estimate(LocalDate start, LocalDate end) {
        if (start == null || end == null || start.isAfter(end)) {
            return new WorkingDaysEstimateDto(start, end, 0, List.of(), List.of());
        }

        int deductedDays = 0;
        List<LocalDate> excludedWeekendDates = new ArrayList<>();
        List<LocalDate> excludedHolidayDates = new ArrayList<>();
        LocalDate current = start;
        while (!current.isAfter(end)) {
            DayOfWeek day = current.getDayOfWeek();
            if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
                excludedWeekendDates.add(current);
            } else if (isPublicHoliday(current)) {
                excludedHolidayDates.add(current);
            } else {
                deductedDays++;
            }
            current = current.plusDays(1);
        }
        return new WorkingDaysEstimateDto(start, end, deductedDays, excludedWeekendDates, excludedHolidayDates);
    }

    public boolean isPublicHoliday(LocalDate date) {
        return date != null && publicHolidayRepository.existsByCountryCodeAndHolidayDateAndActiveTrue(TUNISIA, date);
    }
}
