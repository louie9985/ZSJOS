package cn.iocoder.yudao.module.zsjos.service.performance;

import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.PerformanceFact;
import java.util.*;
import java.math.BigDecimal;
import static cn.iocoder.yudao.module.zsjos.service.performance.PerformancePeriods.*;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.SOURCE_SALES_SELF;

/** One population for both the displayed ratio and its lead-level drilldown. */
public final class PerformanceConversion {
    private static final BigDecimal MINIMUM_ORDER_AMOUNT = new BigDecimal("1280");
    private PerformanceConversion() {}
    public record Row(PerformanceFact receipt, PerformanceFact order, boolean previous) {}
    private record ReceiptKey(Long lead, Long user, java.time.LocalDateTime received) {}
    public static final class Population {
        private final List<PerformanceFact> orders;
        private final List<PerformanceFact> receipts;
        private final Map<ReceiptKey, PerformanceFact> byReceipt = new HashMap<>();
        private final Map<Window, Map<Long, Row>> windows = new HashMap<>();
        public Population(List<PerformanceFact> orders, List<PerformanceFact> receipts) {
            this.orders = orders; this.receipts = receipts;
            for (var r : receipts) byReceipt.putIfAbsent(new ReceiptKey(r.getLeadId(), r.getUserId(), r.getReceivedAt()), r);
        }
        public Map<Long, Row> at(Window window) {
            return windows.computeIfAbsent(window, w -> calculate(w, orders, receipts, byReceipt));
        }
    }

    // Provider linkage limits conversion only; monetary and order metrics retain these facts.
    private static boolean eligible(PerformanceFact fact) {
        return !SOURCE_SALES_SELF.equals(fact.getSourceType()) || fact.getSourceProviderUserId() != null;
    }

    public static boolean rolling(String key) {
        return Set.of("last7", "last30", "last60", "last90").contains(key);
    }

    public static Map<Long, Row> population(Window window, List<PerformanceFact> orders,
                                           List<PerformanceFact> receipts) {
        return new Population(orders, receipts).at(window);
    }
    private static Map<Long, Row> calculate(Window window, List<PerformanceFact> orders,
                                           List<PerformanceFact> receipts, Map<ReceiptKey, PerformanceFact> byReceipt) {
        Map<Long, Row> result = new LinkedHashMap<>();
        for (var receipt : receipts) {
            if (eligible(receipt) && receipt.getLeadId() != null && window.contains(receipt.getReceivedAt())
                    && Set.of("valid", "converted", "won").contains(Objects.toString(receipt.getStatus(), ""))) {
                result.putIfAbsent(receipt.getLeadId(), new Row(receipt, null, false));
            }
        }
        for (var order : orders) {
            if (!eligible(order) || !"first_purchase".equals(order.getOrderType()) || order.getLeadId() == null
                    || order.getAmount() == null || order.getAmount().compareTo(MINIMUM_ORDER_AMOUNT) < 0
                    || !window.contains(order.getOccurredAt()) || order.getReceivedAt() == null) continue;
            boolean cohort = rolling(window.key());
            if (cohort ? !window.contains(order.getReceivedAt()) || order.getOccurredAt().isBefore(order.getReceivedAt())
                    : !withinValidity(order.getReceivedAt(), order.getOccurredAt())) continue;
            // A later transfer of the same Lead is not evidence of this seller's conversion.
            var receipt = byReceipt.get(new ReceiptKey(order.getLeadId(), order.getUserId(), order.getReceivedAt()));
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
