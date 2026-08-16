package cn.iocoder.yudao.framework.common.util.date;

import cn.iocoder.yudao.framework.common.util.date.LocalDateTimeUtils.TimeRange;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LocalDateTimeUtilsTest {

    @Test
    void testDateTimeRangeAndComparisons() {
        LocalDate date = LocalDate.of(2026, 8, 15);

        assertArrayEquals(new LocalDateTime[]{date.atStartOfDay(), date.atTime(LocalTime.MAX)},
                LocalDateTimeUtils.getDateTimeRange(date, date));
        assertTrue(LocalDateTimeUtils.isBetween(date, date, date));
        assertTrue(LocalDateTimeUtils.isClosedRangeOverlap(
                date.atTime(9, 0), date.atTime(10, 0),
                date.atTime(10, 0), date.atTime(11, 0)));
        assertEquals(25, LocalDateTimeUtils.getYearsBetween(
                LocalDate.of(2000, 8, 16), LocalDate.of(2026, 8, 15)));
        assertEquals(YearMonth.of(2026, 8), LocalDateTimeUtils.parseYearMonth("2026-08"));
        assertEquals(LocalDate.of(2026, 8, 1).atStartOfDay(),
                LocalDateTimeUtils.getMonthBeginTime(YearMonth.of(2026, 8)));
        assertEquals(LocalDate.of(2026, 9, 1).atStartOfDay(),
                LocalDateTimeUtils.getNextMonthBeginTime(YearMonth.of(2026, 8)));
    }

    @Test
    void testDailyRangesAndRangeOperations() {
        LocalDate date = LocalDate.of(2026, 8, 15);
        List<TimeRange> dailyRanges = LocalDateTimeUtils.buildDailyTimeRanges(
                date, date, LocalTime.of(22, 0), LocalTime.of(6, 0));

        assertEquals(1, dailyRanges.size());
        assertEquals(480, LocalDateTimeUtils.calculateDurationMinutes(dailyRanges));
        assertNotNull(LocalDateTimeUtils.findDailyTimeRange(
                date, LocalTime.of(22, 0), LocalTime.of(6, 0), date.plusDays(1).atTime(1, 0)));

        TimeRange work = new TimeRange(date.atTime(9, 0), date.atTime(18, 0));
        TimeRange rest = new TimeRange(date.atTime(12, 0), date.atTime(13, 0));
        List<TimeRange> split = LocalDateTimeUtils.subtractTimeRanges(List.of(work), rest);
        assertEquals(2, split.size());
        assertEquals(480, LocalDateTimeUtils.calculateDurationMinutes(split));

        List<TimeRange> merged = LocalDateTimeUtils.mergeTimeRanges(Arrays.asList(
                new TimeRange(date.atTime(10, 0), date.atTime(12, 0)),
                new TimeRange(date.atTime(9, 0), date.atTime(10, 0))));
        assertEquals(1, merged.size());
        assertEquals(date.atTime(9, 0), merged.get(0).getStartTime());
        assertEquals(date.atTime(12, 0), merged.get(0).getEndTime());
    }

}
