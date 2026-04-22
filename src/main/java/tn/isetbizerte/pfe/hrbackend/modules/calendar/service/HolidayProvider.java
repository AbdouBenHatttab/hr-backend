package tn.isetbizerte.pfe.hrbackend.modules.calendar.service;

import java.util.List;

public interface HolidayProvider {
    List<HolidayData> fetchHolidays(String countryCode, int year);
}
