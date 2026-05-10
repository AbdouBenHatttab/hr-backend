package tn.isetbizerte.pfe.hrbackend.modules.hr.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import tn.isetbizerte.pfe.hrbackend.modules.calendar.service.HolidaySyncService;
import tn.isetbizerte.pfe.hrbackend.modules.calendar.service.HolidayProviderFetchException;
import tn.isetbizerte.pfe.hrbackend.modules.hr.service.DashboardRequestSummaryService;
import tn.isetbizerte.pfe.hrbackend.modules.hr.service.HRService;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HRControllerHolidaySyncTest {

    @Test
    void syncPublicHolidays_returnsSyncSummary() {
        HRService hrService = mock(HRService.class);
        DashboardRequestSummaryService summaryService = mock(DashboardRequestSummaryService.class);
        HolidaySyncService holidaySyncService = mock(HolidaySyncService.class);
        HRController controller = new HRController(hrService, summaryService, holidaySyncService);
        Jwt jwt = mock(Jwt.class);

        when(jwt.getClaimAsString("preferred_username")).thenReturn("hr.manager");
        when(holidaySyncService.syncCountryYearDetailed("TN", 2026))
                .thenReturn(new HolidaySyncService.HolidaySyncResult("NAGER_DATE", true, 8,
                        "HolidayAPI free plan cannot access this year. Use fallback provider or configure premium access."));

        Map<String, Object> response = controller.syncPublicHolidays("TN", 2026, jwt).getBody();

        assertThat(response).containsEntry("countryCode", "TN");
        assertThat(response).containsEntry("year", 2026);
        assertThat(response).containsEntry("syncedCount", 8);
        assertThat(response).containsEntry("providerUsed", "NAGER_DATE");
        assertThat(response).containsEntry("fallbackUsed", true);
        assertThat(response).containsEntry("message", "HolidayAPI free plan cannot access this year. Use fallback provider or configure premium access.");
    }

    @Test
    void syncPublicHolidays_returnsBadGatewayWhenProviderFailsWithoutFallback() {
        HRService hrService = mock(HRService.class);
        DashboardRequestSummaryService summaryService = mock(DashboardRequestSummaryService.class);
        HolidaySyncService holidaySyncService = mock(HolidaySyncService.class);
        HRController controller = new HRController(hrService, summaryService, holidaySyncService);
        Jwt jwt = mock(Jwt.class);

        when(jwt.getClaimAsString("preferred_username")).thenReturn("hr.manager");
        when(holidaySyncService.syncCountryYearDetailed("TN", 2026))
                .thenThrow(new HolidayProviderFetchException("HolidayAPI request failed while fetching public holidays.", new RuntimeException("network")));

        var responseEntity = controller.syncPublicHolidays("TN", 2026, jwt);

        assertThat(responseEntity.getStatusCode().value()).isEqualTo(502);
        assertThat(responseEntity.getBody()).containsEntry("success", false);
        assertThat(responseEntity.getBody()).containsEntry("message", "Public holiday sync failed. Please use the fallback provider or configure premium access.");
    }
}
