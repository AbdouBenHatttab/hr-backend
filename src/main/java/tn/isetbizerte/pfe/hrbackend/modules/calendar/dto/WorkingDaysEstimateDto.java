package tn.isetbizerte.pfe.hrbackend.modules.calendar.dto;

import java.time.LocalDate;
import java.util.List;

public class WorkingDaysEstimateDto {
    private LocalDate startDate;
    private LocalDate endDate;
    private int deductedDays;
    private List<LocalDate> excludedWeekendDates;
    private List<LocalDate> excludedHolidayDates;

    public WorkingDaysEstimateDto(LocalDate startDate,
                                  LocalDate endDate,
                                  int deductedDays,
                                  List<LocalDate> excludedWeekendDates,
                                  List<LocalDate> excludedHolidayDates) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.deductedDays = deductedDays;
        this.excludedWeekendDates = excludedWeekendDates;
        this.excludedHolidayDates = excludedHolidayDates;
    }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public int getDeductedDays() { return deductedDays; }
    public void setDeductedDays(int deductedDays) { this.deductedDays = deductedDays; }

    public List<LocalDate> getExcludedWeekendDates() { return excludedWeekendDates; }
    public void setExcludedWeekendDates(List<LocalDate> excludedWeekendDates) { this.excludedWeekendDates = excludedWeekendDates; }

    public List<LocalDate> getExcludedHolidayDates() { return excludedHolidayDates; }
    public void setExcludedHolidayDates(List<LocalDate> excludedHolidayDates) { this.excludedHolidayDates = excludedHolidayDates; }
}
