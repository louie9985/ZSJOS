package cn.iocoder.yudao.module.zsjos.service.performance;

import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.PerformanceFact;
import java.util.*;
import static cn.iocoder.yudao.module.zsjos.service.performance.PerformancePeriods.*;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.SOURCE_SALES_SELF;

/** One population for both the displayed ratio and its lead-level drilldown. */
public final class PerformanceConversion {
    private PerformanceConversion() {}
    public record Row(PerformanceFact receipt, PerformanceFact order, boolean previous) {}

    // Provider linkage limits conversion only; monetary and order metrics retain these facts.
    private static boolean eligible(PerformanceFact fact) {
        return !SOURCE_SALES_SELF.equals(fact.getSourceType()) || fact.getSourceProviderUserId() != null;
    }

    public static boolean rolling(String key) {
        return Set.of("last7", "last30", "last60", "last90").contains(key);
    }

    public static Map<Long, Row> population(Window window, List<PerformanceFact> orders,
                                           List<PerformanceFact> receipts) {
        Map<Long, Row> result = new LinkedHashMap<>();
        for (var receipt : receipts) {
            if (eligible(receipt) && receipt.getLeadId() != null && window.contains(receipt.getReceivedAt())
                    && Set.of("valid", "converted", "won").contains(Objects.toString(receipt.getStatus(), ""))) {
                result.putIfAbsent(receipt.getLeadId(), new Row(receipt, null, false));
            }
        }
        for (var order : orders) {
            if (!eligible(order) || !"first_purchase".equals(order.getOrderType()) || order.getLeadId() == null
                    || !window.contains(order.getOccurredAt()) || order.getReceivedAt() == null) continue;
            boolean cohort = rolling(window.key());
            if (cohort ? !window.contains(order.getReceivedAt()) || order.getOccurredAt().isBefore(order.getReceivedAt())
                    : !withinValidity(order.getReceivedAt(), order.getOccurredAt())) continue;
            // A later transfer of the same Lead is not evidence of this seller's conversion.
            var receipt = receipts.stream().filter(r -> Objects.equals(r.getLeadId(), order.getLeadId())
                    && Objects.equals(r.getUserId(), order.getUserId())
                    && Objects.equals(r.getReceivedAt(), order.getReceivedAt())).findFirst().orElse(null);
            var existing = result.get(order.getLeadId());
            if (cohort && (receipt == null || existing == null)) continue;
            if (existing != null && existing.order() != null
                    && !order.getOccurredAt().isBefore(existing.order().getOccurredAt())) continue;
            result.put(order.getLeadId(), new Row(receipt != null ? receipt : existing == null ? null : existing.receipt(),
                    order, order.getReceivedAt().isBefore(window.start())));
        }
        return result;
    }
}
