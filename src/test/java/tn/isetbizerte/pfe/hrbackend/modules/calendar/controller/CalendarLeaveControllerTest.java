package tn.isetbizerte.pfe.hrbackend.modules.calendar.controller;

import org.junit.jupiter.api.Test;
import tn.isetbizerte.pfe.hrbackend.modules.calendar.dto.PublicHolidayDto;
import tn.isetbizerte.pfe.hrbackend.modules.calendar.entity.PublicHoliday;
import tn.isetbizerte.pfe.hrbackend.modules.calendar.repository.PublicHolidayRepository;
import tn.isetbizerte.pfe.hrbackend.modules.calendar.dto.WorkingDaysEstimateDto;
import tn.isetbizerte.pfe.hrbackend.modules.calendar.service.CalendarLeaveService;
import tn.isetbizerte.pfe.hrbackend.modules.calendar.service.WorkingDayService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CalendarLeaveControllerTest {

    @Test
    void estimateWorkingDays_returnsWorkingDayEstimatePayload() {
        CalendarLeaveService calendarLeaveService = mock(CalendarLeaveService.class);
        WorkingDayService workingDayService = mock(WorkingDayService.class);
        PublicHolidayRepository holidayRepository = mock(PublicHolidayRepository.class);
        CalendarLeaveController controller = new CalendarLeaveController(calendarLeaveService, workingDayService, holidayRepository);
        LocalDate start = LocalDate.of(2026, 5, 21);
        LocalDate end = LocalDate.of(2026, 5, 22);
        when(workingDayService.estimate(start, end))
                .thenReturn(new WorkingDaysEstimateDto(start, end, 2, List.of(), List.of()));

        Map<String, Object> response = controller.estimateWorkingDays(start, end).getBody();

        assertThat(response).containsEntry("success", true);
        assertThat(response).containsKey("data");
        WorkingDaysEstimateDto estimate = (WorkingDaysEstimateDto) response.get("data");
        assertThat(estimate.getDeductedDays()).isEqualTo(2);
    }

    @Test
    void getPublicHolidays_returnsActiveHolidayDtos() {
        CalendarLeaveService calendarLeaveService = mock(CalendarLeaveService.class);
        WorkingDayService workingDayService = mock(WorkingDayService.class);
        PublicHolidayRepository holidayRepository = mock(PublicHolidayRepository.class);
        CalendarLeaveController controller = new CalendarLeaveController(calendarLeaveService, workingDayService, holidayRepository);

        LocalDate start = LocalDate.of(2026, 3, 1);
        LocalDate end = LocalDate.of(2026, 3, 31);
        PublicHoliday fixedHoliday = new PublicHoliday();
        fixedHoliday.setHolidayDate(LocalDate.of(2026, 3, 20));
        fixedHoliday.setName("Independence Day");
        fixedHoliday.setCountryCode("TN");
        fixedHoliday.setYear(2026);
        fixedHoliday.setTentative(false);
        fixedHoliday.setSource("NAGER_DATE");

        PublicHoliday supplementalHoliday = new PublicHoliday();
        supplementalHoliday.setHolidayDate(LocalDate.of(2026, 3, 21));
        supplementalHoliday.setName("Second Day of the Festival of Breaking the Fast");
        supplementalHoliday.setCountryCode("TN");
        supplementalHoliday.setYear(2026);
        supplementalHoliday.setTentative(true);
        supplementalHoliday.setSource("TUNISIA_SUPPLEMENTAL");

        when(holidayRepository.findAllByCountryCodeAndHolidayDateBetweenAndActiveTrueOrderByHolidayDateAsc("TN", start, end))
                .thenReturn(List.of(fixedHoliday, supplementalHoliday));

        Map<String, Object> response = controller.getPublicHolidays("TN", start, end).getBody();

        assertThat(response).containsEntry("success", true);
        assertThat(response).containsEntry("countryCode", "TN");
        assertThat(response).containsEntry("count", 2);
        @SuppressWarnings("unchecked")
        List<PublicHolidayDto> data = (List<PublicHolidayDto>) response.get("data");
        assertThat(data).hasSize(2);
        assertThat(data).extracting(PublicHolidayDto::source)
                .containsExactly("NAGER_DATE", "TUNISIA_SUPPLEMENTAL");
        assertThat(data).extracting(PublicHolidayDto::tentative)
                .containsExactly(false, true);
    }
}
