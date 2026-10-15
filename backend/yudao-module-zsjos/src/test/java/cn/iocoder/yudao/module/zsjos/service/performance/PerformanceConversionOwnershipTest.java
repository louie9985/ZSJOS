package cn.iocoder.yudao.module.zsjos.service.performance;

import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.PerformanceFact;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PerformanceConversionOwnershipTest {
    private final LocalDateTime now = LocalDateTime.parse("2026-10-15T12:00:00");
    private PerformanceFact receipt(long lead, long user, LocalDateTime at, boolean current) {
        var f = new PerformanceFact();
        f.setLeadId(lead); f.setUserId(user); f.setOwnerUserId(user);
        f.setCurrentAssignment(current); f.setReceivedAt(at); f.setStatus("valid");
        f.setSourceType("internal_new_media");
        return f;
    }
    private PerformanceFact order(long lead, long user, LocalDateTime at, LocalDateTime submitted, boolean current) {
        var f = receipt(lead, user, at, current);
        f.setOccurredAt(submitted); f.setOrderType("first_purchase"); f.setAmount(new BigDecimal("1280"));
        return f;
    }
    @Test void transfersRecyclesAndConvertedTransfersLeaveBothSidesOfEveryWindow() {
        for (String key : List.of("lastWeek", "month", "lastMonth", "last7", "last30", "last60", "last90")) {
            var w = PerformancePeriods.window(key, now); var at = w.start().plusHours(1);
            var held = receipt(1, 1, at, true);
            var transferred = receipt(2, 1, at, false); transferred.setOwnerUserId(2L);
            var recycled = receipt(3, 1, at, false); recycled.setOwnerUserId(null);
            var receipts = List.of(held, held, transferred, recycled);
            var orders = List.of(order(1, 1, at, at.plusHours(1), true),
                    order(2, 1, at, at.plusHours(1), false), order(3, 1, at, at.plusHours(1), false));
            var m = PerformanceStatisticsService.metric(w, orders, receipts);
            assertEquals(1, m.denominator(), key); assertEquals(1, m.converted(), key);
            assertEquals(new BigDecimal("1.000000"), m.rate());
            assertEquals(3, m.orders()); assertEquals(new BigDecimal("3840"), m.amount());
        }
    }
    @Test void returningOwnerUsesNewRoundAndOldOrderCannotConvertIt() {
        var old = now.minusDays(35); var back = now.minusDays(2);
        var receipts = List.of(receipt(1, 1, old, false), receipt(1, 2, old.plusDays(10), false),
                receipt(1, 1, back, true), receipt(1, 1, back, true));
        var orders = List.of(order(1, 1, old, old.plusDays(1), false));
        assertTrue(PerformanceConversion.population(PerformancePeriods.window("lastMonth", now), orders, receipts).isEmpty());
        var pop = PerformanceConversion.population(PerformancePeriods.window("month", now), orders, receipts);
        assertEquals(1, pop.size()); assertEquals(back, pop.get(1L).receipt().getReceivedAt());
        assertNull(pop.get(1L).order()); assertFalse(pop.get(1L).previous());
    }
    @Test void transferredPreviousOrdersAndSourceDefectsCannotSupplementOrBlock() {
        var at = now.minusDays(35); var w = PerformancePeriods.window("month", now);
        var old = receipt(1, 1, at, false); old.setSourceType("sales_self_sourced");
        old.setSourceProviderRecorded(false);
        var sold = order(1, 1, at, now.minusDays(1), false); sold.setSourceType("sales_self_sourced");
        sold.setSourceProviderRecorded(false);
        var assessment = new PerformanceConversion.Population(List.of(sold), List.of(old)).assessment(w);
        assertTrue(assessment.ready()); assertTrue(assessment.eligible().isEmpty());
        assertNull(PerformanceStatisticsService.metric(w, List.of(sold), List.of(old)).rate());
    }
    @Test void missingOwnershipEvidenceIsNeverAssumedCurrent() {
        var r = receipt(1, 1, now.minusDays(1), true); r.setCurrentAssignment(null);
        var o = order(1, 1, r.getReceivedAt(), now.minusHours(1), true); o.setOwnerUserId(null);
        assertTrue(PerformanceConversion.population(PerformancePeriods.window("month", now), List.of(o), List.of(r)).isEmpty());
    }
}
