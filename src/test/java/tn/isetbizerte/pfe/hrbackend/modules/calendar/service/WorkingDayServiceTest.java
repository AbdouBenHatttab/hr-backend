package tn.isetbizerte.pfe.hrbackend.modules.calendar.service;

import org.junit.jupiter.api.Test;
import tn.isetbizerte.pfe.hrbackend.modules.calendar.repository.PublicHolidayRepository;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkingDayServiceTest {

    private final PublicHolidayRepository holidayRepository = mock(PublicHolidayRepository.class);
    private final WorkingDayService service = new WorkingDayService(holidayRepository);

    @Test
    void isWorkingDay_excludesSaturdaySundayAndTunisianPublicHoliday() {
        LocalDate saturday = LocalDate.of(2026, 4, 18);
        LocalDate sunday = LocalDate.of(2026, 4, 19);
        LocalDate tunisianHoliday = LocalDate.of(2026, 3, 20);
        LocalDate normalMonday = LocalDate.of(2026, 4, 20);

        when(holidayRepository.existsByCountryCodeAndHolidayDateAndActiveTrue("TN", tunisianHoliday))
                .thenReturn(true);

        assertThat(service.isWorkingDay(saturday)).isFalse();
        assertThat(service.isWorkingDay(sunday)).isFalse();
        assertThat(service.isWorkingDay(tunisianHoliday)).isFalse();
        assertThat(service.isWorkingDay(normalMonday)).isTrue();
    }

    @Test
    void countWorkingDays_countsOnlyWeekdaysThatAreNotTunisianPublicHolidays() {
        LocalDate start = LocalDate.of(2026, 3, 19);
        LocalDate holiday = LocalDate.of(2026, 3, 20);
        LocalDate end = LocalDate.of(2026, 3, 23);
        when(holidayRepository.existsByCountryCodeAndHolidayDateAndActiveTrue("TN", holiday))
                .thenReturn(true);

        assertThat(service.countWorkingDays(start, end)).isEqualTo(2);
    }
}
