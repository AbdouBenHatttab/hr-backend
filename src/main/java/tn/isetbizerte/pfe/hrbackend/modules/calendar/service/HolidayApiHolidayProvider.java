package tn.isetbizerte.pfe.hrbackend.modules.calendar.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@Primary
@ConditionalOnProperty(name = "app.holidays.provider", havingValue = "holidayapi", matchIfMissing = true)
public class HolidayApiHolidayProvider implements HolidayProvider {
    public static final String SOURCE = "HOLIDAY_API";

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String baseUrl;

    public HolidayApiHolidayProvider(RestTemplate restTemplate,
                                     @Value("${app.holidays.holidayapi.api-key:}") String apiKey,
                                     @Value("${app.holidays.holidayapi.base-url:https://holidayapi.com/v1}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
    }

    @Override
    public List<HolidayData> fetchHolidays(String countryCode, int year) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("HolidayAPI key is not configured");
        }

        String normalizedCountryCode = normalizeCountryCode(countryCode);
        String url = UriComponentsBuilder.fromUriString(baseUrl)
                .path("/holidays")
                .queryParam("key", apiKey)
                .queryParam("country", normalizedCountryCode)
                .queryParam("year", year)
                .queryParam("public", true)
                .build(true)
                .toUriString();

        HolidayApiResponse response;
        try {
            response = restTemplate.getForObject(url, HolidayApiResponse.class);
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode().value() == 402) {
                throw new HolidayProviderPlanLimitException(
                        "HolidayAPI free plan cannot access this year. Use fallback provider or configure premium access."
                );
            }
            throw new HolidayProviderFetchException("HolidayAPI request failed while fetching public holidays.", ex);
        } catch (Exception ex) {
            throw new HolidayProviderFetchException("HolidayAPI request failed while fetching public holidays.", ex);
        }

        if (response == null || response.holidays() == null || response.holidays().isEmpty()) {
            return List.of();
        }

        Map<String, HolidayData> uniqueBySourceKey = new LinkedHashMap<>();
        for (HolidayApiHoliday holiday : response.holidays()) {
            if (holiday == null || !Boolean.TRUE.equals(holiday.publicHoliday())) {
                continue;
            }

            LocalDate holidayDate = holiday.observed() != null ? holiday.observed() : holiday.date();
            if (holidayDate == null) {
                continue;
            }

            String displayName = displayName(holiday);
            String sourceKey = sourceKey(normalizedCountryCode, year, holiday, holidayDate, displayName);
            uniqueBySourceKey.putIfAbsent(sourceKey, new HolidayData(
                    holidayDate,
                    displayName,
                    normalizedCountryCode,
                    year,
                    Boolean.FALSE,
                    SOURCE,
                    sourceKey
            ));
        }

        return new ArrayList<>(uniqueBySourceKey.values());
    }

    private String normalizeCountryCode(String countryCode) {
        return (countryCode == null ? "TN" : countryCode.trim().toUpperCase(Locale.ROOT));
    }

    private String displayName(HolidayApiHoliday holiday) {
        if (holiday.name() != null && !holiday.name().isBlank()) {
            return holiday.name().trim();
        }
        return "Public Holiday";
    }

    private String sourceKey(String countryCode, int year, HolidayApiHoliday holiday, LocalDate holidayDate, String displayName) {
        if (holiday.uuid() != null && !holiday.uuid().isBlank()) {
            return holiday.uuid().trim();
        }
        return "%s:%d:%s:%s".formatted(
                countryCode,
                year,
                holidayDate,
                displayName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\p{IsAlphabetic}]+", "-").replaceAll("^-+|-+$", "")
        );
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HolidayApiResponse(
            Integer status,
            List<HolidayApiHoliday> holidays
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HolidayApiHoliday(
            String name,
            LocalDate date,
            LocalDate observed,
            @JsonProperty("public") Boolean publicHoliday,
            String country,
            String uuid
    ) {
    }
}
