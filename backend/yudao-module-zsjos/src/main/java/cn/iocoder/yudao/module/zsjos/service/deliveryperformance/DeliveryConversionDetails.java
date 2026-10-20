package cn.iocoder.yudao.module.zsjos.service.deliveryperformance;

import cn.iocoder.yudao.module.zsjos.controller.admin.deliveryperformance.vo.DeliveryConversionDetailVO.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryperformance.DeliveryConversionSnapshotMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Filters are projections of the completed population, so they cannot change the winning order or denominator. */
public final class DeliveryConversionDetails {
    private DeliveryConversionDetails() {}

    public static Response reviewRequired(DeliveryPerformancePeriods.Window window, LocalDateTime now, long count) {
        return new Response(List.of(), 0, now, window.start(), window.end(),
                new Summary(null, null, null, "review_required", count),
                new Options(List.of(), List.of(), List.of(), List.of(), List.of()));
    }

    public static String snapshotKey(String value) {
        return value == null || value.isBlank() ? "missing" : "value:" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
    private static String label(String value, String missing) { return value == null || value.isBlank() ? missing : value; }

    public static Response assemble(Query query, DeliveryPerformancePeriods.Window window, LocalDateTime now,
            Map<Long, DeliveryPerformanceConversion.Row> population, List<DeliveryConversionSnapshotMapper.Lead> leads,
            List<DeliveryConversionSnapshotMapper.Product> products) {
        var leadMap = leads.stream().collect(Collectors.toMap(DeliveryConversionSnapshotMapper.Lead::getId, Function.identity()));
        var productMap = products.stream().collect(Collectors.groupingBy(DeliveryConversionSnapshotMapper.Product::getOrderId));
        List<Row> rows = population.entrySet().stream().map(entry -> {
            var person = entry.getValue(); var lead = leadMap.get(entry.getKey()); var fact = person.order();
            String category = lead == null ? null : lead.getCategoryLabel(), channel = lead == null ? null : lead.getChannelLabel();
            Order order = fact == null ? null : new Order(fact.getId(), fact.getNumber(), fact.getOccurredAt(), fact.getAmount(),
                    productMap.getOrDefault(fact.getId(), List.of()).stream().map(p -> new Product(
                            snapshotKey(p.getRef()) + "." + snapshotKey(p.getLabel()), p.getRef(),
                            label(p.getLabel(), "商品名称快照缺失"), p.getSnapshot())).distinct().toList());
            return new Row(entry.getKey(), lead == null ? null : lead.getLeadNo(), snapshotKey(category),
                    label(category, "客资分类快照缺失"), snapshotKey(channel), label(channel, "来源渠道快照缺失"),
                    person.previous() ? "previous" : "current",
                    person.receipt() == null ? fact.getReceivedAt() : person.receipt().getReceivedAt(), fact != null, order);
        }).sorted(Comparator.comparing(Row::receivedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Row::leadId, Comparator.reverseOrder())).toList();
        long converted = rows.stream().filter(Row::converted).count();
        var summary = new Summary(converted, rows.size(), DeliveryPerformancePeriods.ratio(java.math.BigDecimal.valueOf(converted), java.math.BigDecimal.valueOf(rows.size())));
        var options = new Options(
                List.of(new Option("converted", "已成交", converted), new Option("unconverted", "未成交", rows.size() - converted)),
                List.of(new Option("current", "期间新接", rows.stream().filter(r -> "current".equals(r.populationKind())).count()),
                        new Option("previous", "往期补入", rows.stream().filter(r -> "previous".equals(r.populationKind())).count())),
                options(rows, r -> Map.of(r.categoryKey(), r.categoryLabel())),
                options(rows, r -> Map.of(r.channelKey(), r.channelLabel())),
                options(rows, r -> r.order() == null ? Map.of() : r.order().products().stream().collect(Collectors.toMap(Product::key, Product::label, (a,b) -> a))));
        String keyword = Objects.toString(query.getKeyword(), "").strip().toLowerCase(Locale.ROOT);
        var filtered = rows.stream().filter(r -> matches(query.getConversionStatus(), r.converted() ? "converted" : "unconverted"))
                .filter(r -> matches(query.getPopulationKind(), r.populationKind()))
                .filter(r -> matches(query.getCategoryKey(), r.categoryKey()))
                .filter(r -> matches(query.getChannelKey(), r.channelKey()))
                .filter(r -> query.getProductKey() == null || r.order() != null && r.order().products().stream().anyMatch(p -> p.key().equals(query.getProductKey())))
                .filter(r -> contains(r.leadNo(), keyword) || r.order() != null && contains(r.order().number(), keyword)).toList();
        var page = filtered.stream().skip((long) (query.getPageNo() - 1) * query.getPageSize()).limit(query.getPageSize()).toList();
        return new Response(page, filtered.size(), now, window.start(), window.end(), summary, options);
    }
    private static boolean matches(String filter, String value) { return filter == null || filter.equals(value); }
    private static boolean contains(String value, String keyword) { return keyword.isEmpty() || value != null && value.toLowerCase(Locale.ROOT).contains(keyword); }
    private static List<Option> options(List<Row> rows, Function<Row, Map<String,String>> values) {
        Map<String, String> labels = new TreeMap<>(); Map<String, Long> counts = new HashMap<>();
        for (var row : rows) values.apply(row).forEach((key, value) -> { labels.putIfAbsent(key, value); counts.merge(key, 1L, Long::sum); });
        return labels.entrySet().stream().map(e -> new Option(e.getKey(), e.getValue(), counts.get(e.getKey()))).toList();
    }
}
