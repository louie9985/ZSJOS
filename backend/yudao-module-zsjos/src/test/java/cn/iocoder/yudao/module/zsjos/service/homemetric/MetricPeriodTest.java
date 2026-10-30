package cn.iocoder.yudao.module.zsjos.service.homemetric;

import org.junit.jupiter.api.Test;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
import static cn.iocoder.yudao.module.zsjos.service.homemetric.MetricPeriod.*;

class MetricPeriodTest {
    @Test void unboundedStatisticsDoNotInventADateFilterOrComparisonPeriod() {
        assertNull(resolve(Kind.ALL, false, Instant.EPOCH, null));
        assertNull(compare(null, Kind.ALL, Comparison.CURRENT));
        assertThrows(IllegalArgumentException.class, () -> compare(null, Kind.ALL, Comparison.YEAR_AGO));
    }
    @Test void fullDayBeforeLeapDayDoesNotCollapseInYearComparison() {
        Range range = resolve(Kind.DAY, false, Instant.parse("2024-02-28T05:00:00Z"), null);
        Range compared = compare(range, Kind.DAY, Comparison.YEAR_AGO);
        assertEquals(LocalDateTime.parse("2023-02-28T00:00:00"), compared.start());
        assertEquals(LocalDateTime.parse("2023-03-01T00:00:00"), compared.end());
    }
    @Test void usesBeijingDateAcrossUtcMidnight() {
        Range range = resolve(Kind.DAY, false, Instant.parse("2026-10-27T18:00:00Z"), null);
        assertEquals(LocalDateTime.parse("2026-10-28T00:00:00"), range.start());
        assertEquals(LocalDateTime.parse("2026-10-29T00:00:00"), range.end());
    }
    @Test void preservesNaturalMonthBoundariesForPreviousPeriod() {
        Range range = resolve(Kind.MONTH, false, Instant.parse("2024-03-31T05:00:00Z"), null);
        Range previous = compare(range, Kind.MONTH, Comparison.PREVIOUS);
        assertEquals(LocalDateTime.parse("2024-02-01T00:00:00"), previous.start());
        assertEquals(LocalDateTime.parse("2024-03-01T00:00:00"), previous.end());
    }
    @Test void midnightToDateIsEmptyInsteadOfReportingConfigurationFailure() {
        Range range = resolve(Kind.DAY, true, Instant.parse("2026-10-27T16:00:00Z"), null);
        assertEquals(range.start(), range.end());
    }
    @Test void customPreviousPeriodHasEqualDurationAndNoOverlap() {
        Range range = new Range(LocalDateTime.parse("2026-10-10T00:00:00"), LocalDateTime.parse("2026-10-13T00:00:00"));
        Range previous = compare(range, Kind.CUSTOM, Comparison.PREVIOUS);
        assertEquals(range.start(), previous.end());
        assertEquals(Duration.between(range.start(), range.end()), Duration.between(previous.start(), previous.end()));
    }
    @Test void calendarYearComparisonClampsLeapDate() {
        Range range = new Range(LocalDateTime.parse("2024-02-01T00:00:00"), LocalDateTime.parse("2024-02-29T12:00:00"));
        assertEquals(LocalDateTime.parse("2023-02-28T12:00:00"), compare(range, Kind.MONTH, Comparison.YEAR_AGO).end());
    }
    @Test void mondayAndQuarterBoundariesAreExplicit() {
        Instant now = Instant.parse("2026-10-28T05:00:00Z");
        assertEquals(LocalDateTime.parse("2026-10-26T00:00:00"), resolve(Kind.WEEK, false, now, null).start());
        Range quarter = resolve(Kind.QUARTER, false, now, null);
        assertEquals(LocalDateTime.parse("2026-10-01T00:00:00"), quarter.start());
        assertEquals(LocalDateTime.parse("2027-01-01T00:00:00"), quarter.end());
    }
}
