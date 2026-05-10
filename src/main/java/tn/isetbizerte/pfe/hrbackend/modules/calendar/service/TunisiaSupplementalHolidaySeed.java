package tn.isetbizerte.pfe.hrbackend.modules.calendar.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

final class TunisiaSupplementalHolidaySeed {
    static final String SOURCE = "TUNISIA_SUPPLEMENTAL";
    private static final String COUNTRY_CODE = "TN";
    private static final int SUPPLEMENTAL_YEAR = 2026;

    private TunisiaSupplementalHolidaySeed() {
    }

    static List<HolidayData> fetchHolidays(String countryCode, int year) {
        if (!COUNTRY_CODE.equals(normalizeCountryCode(countryCode)) || year != SUPPLEMENTAL_YEAR) {
            return List.of();
        }

        return List.of(
                seed("2026-03-20", "Festival of Breaking the Fast"),
                seed("2026-03-21", "Second Day of the Festival of Breaking the Fast"),
                seed("2026-03-22", "Third Day of the Festival of Breaking the Fast"),
                seed("2026-05-28", "Feast of the Sacrifice"),
                seed("2026-05-29", "Second Day of the Feast of the Sacrifice"),
                seed("2026-05-30", "Third Day of the Feast of the Sacrifice")
        );
    }

    private static HolidayData seed(String isoDate, String name) {
        LocalDate date = LocalDate.parse(isoDate);
        return new HolidayData(
                date,
                name,
                COUNTRY_CODE,
                SUPPLEMENTAL_YEAR,
                true,
                SOURCE,
                "%s:%d:%s:%s".formatted(
                        COUNTRY_CODE,
                        SUPPLEMENTAL_YEAR,
                        isoDate,
                        slug(name)
                )
        );
    }

    private static String slug(String value) {
        return value == null ? "holiday" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\p{IsAlphabetic}]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    private static String normalizeCountryCode(String countryCode) {
        return countryCode == null ? COUNTRY_CODE : countryCode.trim().toUpperCase(Locale.ROOT);
    }
}
