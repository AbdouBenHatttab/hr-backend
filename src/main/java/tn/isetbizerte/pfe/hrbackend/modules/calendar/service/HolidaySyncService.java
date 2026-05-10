package tn.isetbizerte.pfe.hrbackend.modules.calendar.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.isetbizerte.pfe.hrbackend.modules.calendar.entity.PublicHoliday;
import tn.isetbizerte.pfe.hrbackend.modules.calendar.repository.PublicHolidayRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class HolidaySyncService {
    private static final Logger log = LoggerFactory.getLogger(HolidaySyncService.class);
    private static final String TUNISIA = "TN";
    private static final String HOLIDAY_API_PROVIDER = "holidayapi";
    private static final String TUNISIA_SUPPLEMENTAL_SOURCE = "TUNISIA_SUPPLEMENTAL";

    private final HolidayProvider holidayProvider;
    private final NagerDateHolidayProvider nagerDateHolidayProvider;
    private final PublicHolidayRepository holidayRepository;
    private final boolean syncOnStartup;
    private final String providerName;

    public HolidaySyncService(HolidayProvider holidayProvider,
                              NagerDateHolidayProvider nagerDateHolidayProvider,
                              PublicHolidayRepository holidayRepository,
                              @Value("${app.holidays.provider:nager}") String providerName,
                              @Value("${app.holidays.sync-on-startup:true}") boolean syncOnStartup) {
        this.holidayProvider = holidayProvider;
        this.nagerDateHolidayProvider = nagerDateHolidayProvider;
        this.holidayRepository = holidayRepository;
        this.providerName = providerName == null ? "nager" : providerName.trim().toLowerCase(Locale.ROOT);
        this.syncOnStartup = syncOnStartup;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        if (!syncOnStartup) return;
        try {
            syncTunisiaCurrentAndNextYear();
        } catch (Exception e) {
            log.warn("Tunisia holiday startup sync failed; local holiday table remains the runtime source: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "${app.holidays.sync-cron:0 30 2 1 * *}")
    public void scheduledSync() {
        try {
            syncTunisiaCurrentAndNextYear();
        } catch (Exception e) {
            log.warn("Scheduled Tunisia holiday sync failed: {}", e.getMessage());
        }
    }

    @Transactional
    public int syncTunisiaCurrentAndNextYear() {
        int currentYear = LocalDate.now().getYear();
        return syncCountryYear(TUNISIA, currentYear) + syncCountryYear(TUNISIA, currentYear + 1);
    }

    @Transactional
    public int syncTunisiaYear(int year) {
        return syncCountryYear(TUNISIA, year);
    }

    @Transactional
    public int syncCountryYear(String countryCode, int year) {
        return syncCountryYearDetailed(countryCode, year).syncedCount();
    }

    @Transactional
    public HolidaySyncResult syncCountryYearDetailed(String countryCode, int year) {
        String normalizedCountryCode = normalizeCountryCode(countryCode);
        HolidayProvider primaryProvider = resolvePrimaryProvider();
        HolidaySyncResult baseResult;
        try {
            baseResult = syncUsingProvider(primaryProvider, normalizedCountryCode, year, false);
        } catch (HolidayProviderPlanLimitException ex) {
            if (!isHolidayApiPrimary()) {
                throw ex;
            }

            log.warn("HolidayAPI plan limit reached for {} {}. Falling back to Nager.Date.", normalizedCountryCode, year);
            HolidaySyncResult fallbackResult = syncUsingProvider(nagerDateHolidayProvider, normalizedCountryCode, year, true);
            baseResult = new HolidaySyncResult(
                    fallbackResult.providerUsed(),
                    true,
                    fallbackResult.syncedCount(),
                    ex.getMessage()
            );
        }

        int supplementalCount = syncTunisiaSupplementalHolidays(normalizedCountryCode, year);
        if (supplementalCount <= 0) {
            return baseResult;
        }

        return new HolidaySyncResult(
                baseResult.providerUsed(),
                baseResult.fallbackUsed(),
                baseResult.syncedCount() + supplementalCount,
                appendSupplementalMessage(baseResult.message(), supplementalCount)
        );
    }

    private HolidaySyncResult syncUsingProvider(HolidayProvider provider, String countryCode, int year, boolean fallback) {
        List<HolidayData> holidays = provider.fetchHolidays(countryCode, year);
        return syncHolidayData(providerNameFor(provider), holidays, fallback, countryCode, year);
    }

    private HolidaySyncResult syncHolidayData(String providerLabel, List<HolidayData> holidays, boolean fallback, String countryCode, int year) {
        if (holidays == null || holidays.isEmpty()) {
            log.info("Synced 0 public holiday row(s) for {} {} via {}", countryCode, year, providerLabel);
            return new HolidaySyncResult(providerLabel, fallback, 0, "No public holidays returned by provider.");
        }

        Map<String, HolidayData> uniqueHolidays = new LinkedHashMap<>();
        for (HolidayData holiday : holidays) {
            if (holiday == null) continue;
            uniqueHolidays.putIfAbsent(holiday.sourceKey(), holiday);
        }

        LocalDateTime now = LocalDateTime.now();
        int count = 0;
        for (HolidayData data : uniqueHolidays.values()) {
            PublicHoliday holiday = holidayRepository.findBySourceAndSourceKey(data.source(), data.sourceKey())
                    .or(() -> holidayRepository.findByCountryCodeAndHolidayDateAndSource(
                            data.countryCode(), data.date(), data.source()))
                    .or(() -> holidayRepository.findByCountryCodeAndHolidayDateAndNameAndSource(
                            data.countryCode(), data.date(), data.name(), data.source()))
                    .orElseGet(PublicHoliday::new);

            holiday.setHolidayDate(data.date());
            holiday.setName(data.name());
            holiday.setCountryCode(data.countryCode());
            holiday.setYear(data.year());
            if (holiday.getId() == null && holiday.getActive() == null) {
                holiday.setActive(true);
            }
            holiday.setTentative(Boolean.TRUE.equals(data.tentative()));
            holiday.setSource(data.source());
            holiday.setSourceKey(data.sourceKey());
            holiday.setLastSyncedAt(now);
            if (holiday.getCreatedAt() == null) holiday.setCreatedAt(now);
            holiday.setUpdatedAt(now);
            holidayRepository.save(holiday);
            count++;
        }

        log.info("Synced {} public holiday row(s) for {} {} via {}", count, countryCode, year, providerLabel);
        return new HolidaySyncResult(providerLabel, fallback, count, "Public holidays synced successfully.");
    }

    private int syncTunisiaSupplementalHolidays(String countryCode, int year) {
        return syncHolidayData(
                TUNISIA_SUPPLEMENTAL_SOURCE,
                TunisiaSupplementalHolidaySeed.fetchHolidays(countryCode, year),
                false,
                countryCode,
                year
        ).syncedCount();
    }

    private String appendSupplementalMessage(String baseMessage, int supplementalCount) {
        String prefix = (baseMessage == null || baseMessage.isBlank())
                ? "Supplemental Tunisia holidays added"
                : baseMessage + " Supplemental Tunisia holidays added";
        return prefix + ": " + supplementalCount + ".";
    }

    private HolidayProvider resolvePrimaryProvider() {
        return isHolidayApiPrimary() ? holidayProvider : nagerDateHolidayProvider;
    }

    private boolean isHolidayApiPrimary() {
        return HOLIDAY_API_PROVIDER.equals(providerName);
    }

    private String providerNameFor(HolidayProvider provider) {
        if (provider instanceof HolidayApiHolidayProvider) {
            return "HOLIDAY_API";
        }
        return "NAGER_DATE";
    }

    private String normalizeCountryCode(String countryCode) {
        return (countryCode == null ? TUNISIA : countryCode.trim().toUpperCase(Locale.ROOT));
    }

    public record HolidaySyncResult(
            String providerUsed,
            boolean fallbackUsed,
            int syncedCount,
            String message
    ) {
    }
}
