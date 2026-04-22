package tn.isetbizerte.pfe.hrbackend.modules.calendar.service;

import java.time.LocalDate;

public record HolidayData(
        LocalDate date,
        String name,
        String countryCode,
        Integer year,
        Boolean tentative,
        String source,
        String sourceKey
) {
}
