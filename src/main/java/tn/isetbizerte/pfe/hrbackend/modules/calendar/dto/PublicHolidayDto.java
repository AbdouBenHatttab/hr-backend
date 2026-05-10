package tn.isetbizerte.pfe.hrbackend.modules.calendar.dto;

import java.time.LocalDate;

public record PublicHolidayDto(
        LocalDate date,
        String name,
        String countryCode,
        Integer year,
        Boolean tentative,
        String source
) {
}
