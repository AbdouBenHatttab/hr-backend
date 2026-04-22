package tn.isetbizerte.pfe.hrbackend.modules.calendar.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class NagerDateHolidayProvider implements HolidayProvider {
    public static final String SOURCE = "NAGER_DATE";

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public NagerDateHolidayProvider(RestTemplate restTemplate,
                                    @Value("${app.holidays.nager-base-url:https://date.nager.at/api/v3}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    @Override
    public List<HolidayData> fetchHolidays(String countryCode, int year) {
        String url = String.format("%s/PublicHolidays/%d/%s", baseUrl, year, countryCode);
        NagerHoliday[] response = restTemplate.getForObject(url, NagerHoliday[].class);
        if (response == null) {
            return List.of();
        }

        Map<String, List<NagerHoliday>> byName = Arrays.stream(response)
                .sorted(Comparator.comparing(NagerHoliday::date))
                .collect(Collectors.groupingBy(this::stableName, LinkedHashMap::new, Collectors.toList()));

        List<HolidayData> holidays = new ArrayList<>();
        for (Map.Entry<String, List<NagerHoliday>> entry : byName.entrySet()) {
            List<NagerHoliday> sameName = entry.getValue();
            for (int i = 0; i < sameName.size(); i++) {
                NagerHoliday holiday = sameName.get(i);
                String sourceKey = "%s:%d:%s:%d".formatted(
                        countryCode.toUpperCase(Locale.ROOT),
                        year,
                        entry.getKey(),
                        i + 1
                );
                holidays.add(new HolidayData(
                        holiday.date(),
                        displayName(holiday),
                        countryCode.toUpperCase(Locale.ROOT),
                        year,
                        false,
                        SOURCE,
                        sourceKey
                ));
            }
        }
        return holidays;
    }

    private String displayName(NagerHoliday holiday) {
        if (holiday.localName() != null && !holiday.localName().isBlank()) return holiday.localName();
        if (holiday.name() != null && !holiday.name().isBlank()) return holiday.name();
        return "Public Holiday";
    }

    private String stableName(NagerHoliday holiday) {
        return displayName(holiday)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\p{IsAlphabetic}]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NagerHoliday(
            LocalDate date,
            String localName,
            String name,
            String countryCode,
            Boolean fixed,
            Boolean global,
            String[] counties,
            Integer launchYear,
            String[] types
    ) {
    }
}
