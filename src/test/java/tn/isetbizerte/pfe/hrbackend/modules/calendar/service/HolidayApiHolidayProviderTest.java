package tn.isetbizerte.pfe.hrbackend.modules.calendar.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HolidayApiHolidayProviderTest {

    @Test
    void fetchHolidays_mapsOnlyPublicHolidayRowsAndUsesObservedDate() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        HolidayApiHolidayProvider provider = new HolidayApiHolidayProvider(restTemplate, "test-key", "https://holidayapi.com/v1");

        HolidayApiHolidayProvider.HolidayApiResponse response = new HolidayApiHolidayProvider.HolidayApiResponse(
                200,
                List.of(
                        new HolidayApiHolidayProvider.HolidayApiHoliday(
                                "Independence Day",
                                LocalDate.of(2026, 3, 20),
                                LocalDate.of(2026, 3, 23),
                                true,
                                "TN",
                                "uuid-123"
                        ),
                        new HolidayApiHolidayProvider.HolidayApiHoliday(
                                "Observance",
                                LocalDate.of(2026, 3, 22),
                                LocalDate.of(2026, 3, 22),
                                false,
                                "TN",
                                "uuid-999"
                        )
                )
        );
        when(restTemplate.getForObject(anyString(), org.mockito.ArgumentMatchers.eq(HolidayApiHolidayProvider.HolidayApiResponse.class)))
                .thenReturn(response);

        List<HolidayData> holidays = provider.fetchHolidays("TN", 2026);

        assertThat(holidays).hasSize(1);
        HolidayData holiday = holidays.get(0);
        assertThat(holiday.date()).isEqualTo(LocalDate.of(2026, 3, 23));
        assertThat(holiday.name()).isEqualTo("Independence Day");
        assertThat(holiday.countryCode()).isEqualTo("TN");
        assertThat(holiday.source()).isEqualTo(HolidayApiHolidayProvider.SOURCE);
        assertThat(holiday.sourceKey()).isEqualTo("uuid-123");
    }

    @Test
    void fetchHolidays_402ThrowsPlanLimitException() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        HolidayApiHolidayProvider provider = new HolidayApiHolidayProvider(restTemplate, "test-key", "https://holidayapi.com/v1");
        when(restTemplate.getForObject(anyString(), org.mockito.ArgumentMatchers.eq(HolidayApiHolidayProvider.HolidayApiResponse.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.PAYMENT_REQUIRED, "Payment Required", null, null, null));

        assertThatThrownBy(() -> provider.fetchHolidays("TN", 2026))
                .isInstanceOf(HolidayProviderPlanLimitException.class)
                .hasMessage("HolidayAPI free plan cannot access this year. Use fallback provider or configure premium access.");
    }
}
