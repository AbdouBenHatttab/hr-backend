package tn.isetbizerte.pfe.hrbackend.modules.calendar.controller;

import org.junit.jupiter.api.Test;
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
        CalendarLeaveController controller = new CalendarLeaveController(calendarLeaveService, workingDayService);
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
}
