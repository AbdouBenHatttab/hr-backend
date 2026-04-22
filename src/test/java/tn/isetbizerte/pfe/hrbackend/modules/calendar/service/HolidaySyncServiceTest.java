package tn.isetbizerte.pfe.hrbackend.modules.calendar.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tn.isetbizerte.pfe.hrbackend.modules.calendar.entity.PublicHoliday;
import tn.isetbizerte.pfe.hrbackend.modules.calendar.repository.PublicHolidayRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class HolidaySyncServiceTest {

    private HolidayProvider provider;
    private PublicHolidayRepository repository;
    private HolidaySyncService service;

    @BeforeEach
    void setUp() {
        provider = mock(HolidayProvider.class);
        repository = mock(PublicHolidayRepository.class);
        service = new HolidaySyncService(provider, repository, false);
        when(repository.save(any(PublicHoliday.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void syncTunisiaYear_insertsHolidaysFromProvider() {
        HolidayData independenceDay = new HolidayData(
                LocalDate.of(2026, 3, 20),
                "Independence Day",
                "TN",
                2026,
                false,
                "TEST",
                "TN:2026:independence-day:1"
        );
        when(provider.fetchHolidays("TN", 2026)).thenReturn(List.of(independenceDay));
        when(repository.findBySourceAndSourceKey("TEST", "TN:2026:independence-day:1")).thenReturn(Optional.empty());
        when(repository.findByCountryCodeAndHolidayDateAndSource("TN", independenceDay.date(), "TEST")).thenReturn(Optional.empty());

        int synced = service.syncTunisiaYear(2026);

        assertThat(synced).isEqualTo(1);
        verify(repository).save(argThat(holiday ->
                holiday.getHolidayDate().equals(independenceDay.date())
                        && holiday.getName().equals("Independence Day")
                        && holiday.getCountryCode().equals("TN")
                        && holiday.getActive()
        ));
    }

    @Test
    void syncTunisiaYear_updatesChangedHolidayDateWhenSourceKeyMatches() {
        PublicHoliday existing = new PublicHoliday();
        existing.setHolidayDate(LocalDate.of(2026, 6, 17));
        existing.setName("Eid al-Adha");
        existing.setCountryCode("TN");
        existing.setYear(2026);
        existing.setSource("TEST");
        existing.setSourceKey("TN:2026:eid-al-adha:1");

        HolidayData changed = new HolidayData(
                LocalDate.of(2026, 6, 18),
                "Eid al-Adha",
                "TN",
                2026,
                true,
                "TEST",
                "TN:2026:eid-al-adha:1"
        );
        when(provider.fetchHolidays("TN", 2026)).thenReturn(List.of(changed));
        when(repository.findBySourceAndSourceKey("TEST", "TN:2026:eid-al-adha:1")).thenReturn(Optional.of(existing));

        service.syncTunisiaYear(2026);

        verify(repository).save(argThat(holiday ->
                holiday == existing
                        && holiday.getHolidayDate().equals(LocalDate.of(2026, 6, 18))
                        && holiday.getTentative()
        ));
    }
}
