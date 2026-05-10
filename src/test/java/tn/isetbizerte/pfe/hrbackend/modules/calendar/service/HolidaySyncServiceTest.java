package tn.isetbizerte.pfe.hrbackend.modules.calendar.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tn.isetbizerte.pfe.hrbackend.modules.calendar.entity.PublicHoliday;
import tn.isetbizerte.pfe.hrbackend.modules.calendar.repository.PublicHolidayRepository;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class HolidaySyncServiceTest {

    private HolidayApiHolidayProvider holidayApiProvider;
    private NagerDateHolidayProvider nagerProvider;
    private PublicHolidayRepository repository;
    private HolidaySyncService service;

    @BeforeEach
    void setUp() {
        holidayApiProvider = mock(HolidayApiHolidayProvider.class);
        nagerProvider = mock(NagerDateHolidayProvider.class);
        repository = mock(PublicHolidayRepository.class);
        service = new HolidaySyncService(holidayApiProvider, nagerProvider, repository, "holidayapi", false);
        when(repository.save(any(PublicHoliday.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void syncTunisiaYear_insertsProviderAndSupplementalHolidays() {
        stubEmptyLookups();

        HolidayData independenceDay = new HolidayData(
                LocalDate.of(2026, 3, 20),
                "Independence Day",
                "TN",
                2026,
                false,
                "TEST",
                "TN:2026:independence-day:1"
        );
        when(holidayApiProvider.fetchHolidays("TN", 2026)).thenReturn(List.of(independenceDay));

        int synced = service.syncTunisiaYear(2026);

        assertThat(synced).isEqualTo(7);
        verify(repository).save(argThat(holiday ->
                holiday.getHolidayDate().equals(independenceDay.date())
                        && holiday.getName().equals("Independence Day")
                        && holiday.getCountryCode().equals("TN")
                        && holiday.getActive()
        ));
        verify(repository, times(6)).save(argThat(holiday ->
                TunisiaSupplementalHolidaySeed.SOURCE.equals(holiday.getSource())
                        && Boolean.TRUE.equals(holiday.getTentative())
        ));
    }

    @Test
    void syncTunisiaYear_insertsSupplementalTunisiaHolidays() {
        stubEmptyLookups();
        when(holidayApiProvider.fetchHolidays("TN", 2026)).thenReturn(List.of());

        int synced = service.syncTunisiaYear(2026);

        assertThat(synced).isEqualTo(6);
        verify(repository, times(6)).save(argThat(holiday ->
                TunisiaSupplementalHolidaySeed.SOURCE.equals(holiday.getSource())
                        && Boolean.TRUE.equals(holiday.getTentative())
        ));
        verify(repository).save(argThat(holiday ->
                holiday.getHolidayDate().equals(LocalDate.of(2026, 3, 20))
                        && holiday.getName().equals("Festival of Breaking the Fast")
                        && holiday.getSource().equals(TunisiaSupplementalHolidaySeed.SOURCE)
        ));
    }

    @Test
    void syncTunisiaYear_doesNotDuplicateSupplementalRowsOnRepeatedSync() {
        Map<String, PublicHoliday> store = new HashMap<>();
        when(repository.save(any(PublicHoliday.class))).thenAnswer(invocation -> {
            PublicHoliday holiday = invocation.getArgument(0);
            store.put(holiday.getSourceKey(), holiday);
            return holiday;
        });
        when(repository.findBySourceAndSourceKey(anyString(), anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(store.get(invocation.getArgument(1))));
        when(repository.findByCountryCodeAndHolidayDateAndSource(anyString(), any(LocalDate.class), anyString()))
                .thenAnswer(invocation -> lookupByCountryDateAndSource(
                        store,
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        invocation.getArgument(2)
                ));
        when(repository.findByCountryCodeAndHolidayDateAndNameAndSource(anyString(), any(LocalDate.class), anyString(), anyString()))
                .thenAnswer(invocation -> lookupByCountryDateNameAndSource(
                        store,
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        invocation.getArgument(2),
                        invocation.getArgument(3)
                ));
        when(holidayApiProvider.fetchHolidays("TN", 2026)).thenReturn(List.of());

        int firstSync = service.syncTunisiaYear(2026);
        int secondSync = service.syncTunisiaYear(2026);

        assertThat(firstSync).isEqualTo(6);
        assertThat(secondSync).isEqualTo(6);
        assertThat(store).hasSize(6);
        assertThat(store.values())
                .allSatisfy(holiday -> {
                    assertThat(holiday.getSource()).isEqualTo(TunisiaSupplementalHolidaySeed.SOURCE);
                    assertThat(holiday.getTentative()).isTrue();
                });
    }

    @Test
    void syncTunisiaYear_updatesChangedHolidayDateWhenSourceKeyMatches() {
        stubEmptyLookups();

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
        when(holidayApiProvider.fetchHolidays("TN", 2026)).thenReturn(List.of(changed));
        when(repository.findBySourceAndSourceKey("TEST", "TN:2026:eid-al-adha:1")).thenReturn(Optional.of(existing));

        int synced = service.syncTunisiaYear(2026);

        assertThat(synced).isEqualTo(7);
        verify(repository).save(argThat(holiday ->
                holiday == existing
                        && holiday.getHolidayDate().equals(LocalDate.of(2026, 6, 18))
                        && holiday.getTentative()
        ));
    }

    @Test
    void syncTunisiaYear_deduplicatesSourceKeysAndPreservesExistingActiveFlag() {
        stubEmptyLookups();

        PublicHoliday existing = new PublicHoliday();
        existing.setHolidayDate(LocalDate.of(2026, 7, 25));
        existing.setName("Republic Day");
        existing.setCountryCode("TN");
        existing.setYear(2026);
        existing.setActive(false);
        existing.setSource("TEST");
        existing.setSourceKey("TN:2026:republic-day:1");

        HolidayData duplicateOne = new HolidayData(
                LocalDate.of(2026, 7, 25),
                "Republic Day",
                "TN",
                2026,
                false,
                "TEST",
                "TN:2026:republic-day:1"
        );
        HolidayData duplicateTwo = new HolidayData(
                LocalDate.of(2026, 7, 25),
                "Republic Day",
                "TN",
                2026,
                false,
                "TEST",
                "TN:2026:republic-day:1"
        );
        when(holidayApiProvider.fetchHolidays("TN", 2026)).thenReturn(List.of(duplicateOne, duplicateTwo));
        when(repository.findBySourceAndSourceKey("TEST", "TN:2026:republic-day:1")).thenReturn(Optional.of(existing));

        int synced = service.syncTunisiaYear(2026);

        assertThat(synced).isEqualTo(7);
        verify(repository, times(1)).save(existing);
        assertThat(existing.getActive()).isFalse();
    }

    @Test
    void syncCountryYearDetailed_fallsBackToNagerDateOnHolidayApiPlanLimit() {
        stubEmptyLookups();

        when(holidayApiProvider.fetchHolidays("TN", 2026))
                .thenThrow(new HolidayProviderPlanLimitException(
                        "HolidayAPI free plan cannot access this year. Use fallback provider or configure premium access."
                ));

        HolidayData fallbackHoliday = new HolidayData(
                LocalDate.of(2026, 3, 20),
                "Independence Day",
                "TN",
                2026,
                false,
                "NAGER_DATE",
                "TN:2026:independence-day:1"
        );
        when(nagerProvider.fetchHolidays("TN", 2026)).thenReturn(List.of(fallbackHoliday));

        HolidaySyncService.HolidaySyncResult result = service.syncCountryYearDetailed("TN", 2026);

        assertThat(result.providerUsed()).isEqualTo("NAGER_DATE");
        assertThat(result.fallbackUsed()).isTrue();
        assertThat(result.syncedCount()).isEqualTo(7);
        assertThat(result.message())
                .contains("HolidayAPI free plan cannot access this year")
                .contains("Supplemental Tunisia holidays added: 6.");
    }

    private void stubEmptyLookups() {
        when(repository.findBySourceAndSourceKey(anyString(), anyString())).thenReturn(Optional.empty());
        when(repository.findByCountryCodeAndHolidayDateAndSource(anyString(), any(LocalDate.class), anyString()))
                .thenReturn(Optional.empty());
        when(repository.findByCountryCodeAndHolidayDateAndNameAndSource(anyString(), any(LocalDate.class), anyString(), anyString()))
                .thenReturn(Optional.empty());
    }

    private Optional<PublicHoliday> lookupByCountryDateAndSource(Map<String, PublicHoliday> store, String countryCode, LocalDate date, String source) {
        return store.values().stream()
                .filter(holiday -> countryMatches(holiday, countryCode))
                .filter(holiday -> date.equals(holiday.getHolidayDate()))
                .filter(holiday -> source.equals(holiday.getSource()))
                .findFirst();
    }

    private Optional<PublicHoliday> lookupByCountryDateNameAndSource(Map<String, PublicHoliday> store, String countryCode, LocalDate date, String name, String source) {
        return store.values().stream()
                .filter(holiday -> countryMatches(holiday, countryCode))
                .filter(holiday -> date.equals(holiday.getHolidayDate()))
                .filter(holiday -> name.equals(holiday.getName()))
                .filter(holiday -> source.equals(holiday.getSource()))
                .findFirst();
    }

    private boolean countryMatches(PublicHoliday holiday, String countryCode) {
        return holiday != null && holiday.getCountryCode() != null && holiday.getCountryCode().equals(countryCode);
    }
}
