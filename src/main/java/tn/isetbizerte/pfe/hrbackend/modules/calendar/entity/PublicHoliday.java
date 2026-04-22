package tn.isetbizerte.pfe.hrbackend.modules.calendar.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "public_holidays",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_public_holiday_country_date_source", columnNames = {"country_code", "holiday_date", "source"}),
                @UniqueConstraint(name = "uk_public_holiday_source_key", columnNames = {"source", "source_key"})
        },
        indexes = {
                @Index(name = "idx_public_holidays_country_year", columnList = "country_code, holiday_year"),
                @Index(name = "idx_public_holidays_date_active", columnList = "holiday_date, active")
        }
)
public class PublicHoliday {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(name = "holiday_year", nullable = false)
    private Integer year;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(nullable = false)
    private Boolean tentative = false;

    @Column(nullable = false, length = 80)
    private String source;

    @Column(name = "source_key", nullable = false, length = 300)
    private String sourceKey;

    @Column(name = "last_synced_at", nullable = false)
    private LocalDateTime lastSyncedAt = LocalDateTime.now();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDate getHolidayDate() { return holidayDate; }
    public void setHolidayDate(LocalDate holidayDate) { this.holidayDate = holidayDate; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }

    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public Boolean getTentative() { return tentative; }
    public void setTentative(Boolean tentative) { this.tentative = tentative; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getSourceKey() { return sourceKey; }
    public void setSourceKey(String sourceKey) { this.sourceKey = sourceKey; }

    public LocalDateTime getLastSyncedAt() { return lastSyncedAt; }
    public void setLastSyncedAt(LocalDateTime lastSyncedAt) { this.lastSyncedAt = lastSyncedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
