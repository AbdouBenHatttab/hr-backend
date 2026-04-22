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
import java.util.List;

@Service
public class HolidaySyncService {
    private static final Logger log = LoggerFactory.getLogger(HolidaySyncService.class);
    private static final String TUNISIA = "TN";

    private final HolidayProvider holidayProvider;
    private final PublicHolidayRepository holidayRepository;
    private final boolean syncOnStartup;

    public HolidaySyncService(HolidayProvider holidayProvider,
                              PublicHolidayRepository holidayRepository,
                              @Value("${app.holidays.sync-on-startup:true}") boolean syncOnStartup) {
        this.holidayProvider = holidayProvider;
        this.holidayRepository = holidayRepository;
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

    @Scheduled(cron = "${app.holidays.sync-cron:0 30 2 * * SUN}")
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
        return syncTunisiaYear(currentYear) + syncTunisiaYear(currentYear + 1);
    }

    @Transactional
    public int syncTunisiaYear(int year) {
        List<HolidayData> holidays = holidayProvider.fetchHolidays(TUNISIA, year);
        LocalDateTime now = LocalDateTime.now();
        int count = 0;
        for (HolidayData data : holidays) {
            PublicHoliday holiday = holidayRepository.findBySourceAndSourceKey(data.source(), data.sourceKey())
                    .or(() -> holidayRepository.findByCountryCodeAndHolidayDateAndSource(
                            data.countryCode(), data.date(), data.source()))
                    .orElseGet(PublicHoliday::new);

            holiday.setHolidayDate(data.date());
            holiday.setName(data.name());
            holiday.setCountryCode(data.countryCode());
            holiday.setYear(data.year());
            holiday.setActive(true);
            holiday.setTentative(Boolean.TRUE.equals(data.tentative()));
            holiday.setSource(data.source());
            holiday.setSourceKey(data.sourceKey());
            holiday.setLastSyncedAt(now);
            if (holiday.getCreatedAt() == null) holiday.setCreatedAt(now);
            holiday.setUpdatedAt(now);
            holidayRepository.save(holiday);
            count++;
        }
        log.info("Synced {} Tunisia public holiday row(s) for {}", count, year);
        return count;
    }
}
