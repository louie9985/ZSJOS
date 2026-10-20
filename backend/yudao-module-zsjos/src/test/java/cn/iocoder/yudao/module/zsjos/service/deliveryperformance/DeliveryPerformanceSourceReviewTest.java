package cn.iocoder.yudao.module.zsjos.service.deliveryperformance;

import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryperformance.DeliveryPerformanceFact;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeliveryPerformanceSourceReviewTest {
    private final LocalDateTime now = LocalDateTime.parse("2026-10-10T12:00:00");
    private DeliveryPerformanceFact receipt(long id, String source, boolean recorded, String at) {
        var r = new DeliveryPerformanceFact(); r.setLeadId(id); r.setUserId(17L); r.setOwnerUserId(17L); r.setCurrentAssignment(true); r.setStatus("valid");
        r.setReceivedAt(LocalDateTime.parse(at)); r.setOccurredAt(r.getReceivedAt());
        r.setSourceType(source); r.setSourceProviderRecorded(recorded); return r;
    }
    private DeliveryPerformanceFact order(DeliveryPerformanceFact receipt, String amount, String at) {
        var o = new DeliveryPerformanceFact(); o.setLeadId(receipt.getLeadId()); o.setUserId(receipt.getUserId()); o.setOwnerUserId(receipt.getOwnerUserId()); o.setCurrentAssignment(receipt.getCurrentAssignment());
        o.setSourceType(receipt.getSourceType()); o.setSourceProviderRecorded(receipt.getSourceProviderRecorded());
        o.setReceivedAt(receipt.getReceivedAt()); o.setOccurredAt(LocalDateTime.parse(at));
        o.setOrderType("first_purchase"); o.setAmount(new BigDecimal(amount)); return o;
    }
    @Test void lowAmountReceiptBlocksOnlyItsPeriodAndDoesNotBlockFinancialMetrics() {
        var r = receipt(1, "sales_self_sourced", false, "2026-09-20T15:53:47");
        var o = order(r, "980", "2026-09-20T16:00:00");
        var metric = DeliveryPerformanceStatisticsService.metric(DeliveryPerformancePeriods.window("lastMonth",now),List.of(o),List.of(r));
        assertEquals("review_required",metric.sourceStatus()); assertEquals(1,metric.sourceReviewCount());
        assertNull(metric.converted()); assertNull(metric.denominator()); assertNull(metric.rate());
        assertEquals(new BigDecimal("980"),metric.amount()); assertEquals(1,metric.orders());
        assertEquals(0,new BigDecimal("980").compareTo(metric.average()));
        assertEquals("ready",DeliveryPerformanceStatisticsService.metric(DeliveryPerformancePeriods.window("month",now),List.of(o),List.of(r)).sourceStatus());
    }
    @Test void lowAmountOldReceiptCannotBlockByBeingImportedThroughAnOrder() {
        var r = receipt(1, "education_self_sourced", false, "2026-08-25T12:00:00");
        for (String amount : List.of("980", "1279.99", "1280")) {
            var assessment = new DeliveryPerformanceConversion.Population(List.of(order(r, amount, "2026-09-20T12:00:00")), List.of(r))
                    .assessment(DeliveryPerformancePeriods.window("lastMonth", now));
            assertEquals(amount.equals("1280") ? 1 : 0,assessment.sourceReviewCount());
        }
    }
    @Test void bothConfirmedUnlinkedSourcesAreExcludedInAllSevenWindowsAndUnknownsAreDeduplicated() {
        for (String key : List.of("lastWeek","month","lastMonth","last7","last30","last60","last90")) {
            var w = DeliveryPerformancePeriods.window(key,now); String at=w.start().plusHours(1).toString();
            var sales=receipt(1,"sales_self_sourced",true,at); var education=receipt(2,"education_self_sourced",true,at);
            var unknown=receipt(3,"education_self_sourced",false,at);
            var media=receipt(4,"internal_new_media",false,at);
            var population=new DeliveryPerformanceConversion.Population(List.of(order(sales,"1280",at),order(education,"1280",at),order(unknown,"1280",at),order(media,"1280",at)),List.of(sales,education,unknown,unknown,media));
            assertEquals(1,population.assessment(w).sourceReviewCount());
            assertEquals(java.util.Set.of(4L),population.at(w).keySet());
        }
    }
}
