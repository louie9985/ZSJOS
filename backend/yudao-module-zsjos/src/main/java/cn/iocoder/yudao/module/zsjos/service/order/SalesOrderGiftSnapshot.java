package cn.iocoder.yudao.module.zsjos.service.order;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.gift.GiftConfigDO;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

/** Persisted gift facts; current catalog values are consulted only for new selections. */
public record SalesOrderGiftSnapshot(String code, String name, List<String> path, String snapshotAt) {
    public static List<SalesOrderGiftSnapshot> read(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            Object decoded = JsonUtils.getObjectMapper().readValue(json, Object.class);
            if (!(decoded instanceof List<?> entries)) throw new IllegalArgumentException();
            List<SalesOrderGiftSnapshot> result = new ArrayList<>();
            Set<String> codes = new HashSet<>();
            for (Object entry : entries) {
                // Earlier writers serialized each snapshot before serializing the outer array.
                if (entry instanceof String value) entry = JsonUtils.getObjectMapper().readValue(value, Object.class);
                if (!(entry instanceof Map<?, ?> map) || !(map.get("code") instanceof String code)
                        || code.isBlank() || !codes.add(code)) throw new IllegalArgumentException();
                String name = optionalString(map.get("name"));
                String at = optionalString(map.get("snapshotAt"));
                List<String> path = null;
                if (map.get("path") != null) {
                    if (!(map.get("path") instanceof List<?> values)
                            || values.stream().anyMatch(value -> !(value instanceof String))) throw new IllegalArgumentException();
                    path = values.stream().map(String.class::cast).toList();
                }
                result.add(new SalesOrderGiftSnapshot(code, name, path, at));
            }
            return List.copyOf(result);
        } catch (RuntimeException ex) {
            // Never log a historical business payload or turn damaged history into an empty selection.
            throw exception(SALES_ORDER_GIFT_SNAPSHOT_INVALID);
        }
    }

    private static String optionalString(Object value) {
        if (value == null || value instanceof String) return (String) value;
        throw new IllegalArgumentException();
    }

    public static List<SalesOrderGiftSnapshot> resolve(List<String> requested, String previousJson,
            Supplier<List<GiftConfigDO>> catalog, LocalDateTime now) {
        Map<String, SalesOrderGiftSnapshot> retained = new LinkedHashMap<>();
        read(previousJson).forEach(item -> retained.put(item.code(), item));
        if (requested == null || requested.isEmpty()) return List.of();
        LinkedHashSet<String> codes = new LinkedHashSet<>(requested);
        if (codes.stream().anyMatch(code -> code == null || code.isBlank())) throw exception(SALES_ORDER_GIFT_SELECTION_INVALID);
        List<GiftConfigDO> all = codes.stream().allMatch(retained::containsKey) ? List.of() : catalog.get();
        Map<String, GiftConfigDO> byCode = new HashMap<>();
        Map<Long, GiftConfigDO> byId = new HashMap<>();
        all.forEach(item -> { byCode.put(item.getCode(), item); byId.put(item.getId(), item); });
        List<SalesOrderGiftSnapshot> result = new ArrayList<>();
        for (String code : codes) {
            if (retained.containsKey(code)) { result.add(retained.get(code)); continue; }
            GiftConfigDO gift = byCode.get(code);
            if (gift == null || !Objects.equals(gift.getStatus(), 0)
                    || all.stream().anyMatch(item -> Objects.equals(item.getParentId(), gift.getId())))
                throw exception(SALES_ORDER_GIFT_SELECTION_INVALID);
            List<String> path = new ArrayList<>();
            Set<Long> seen = new HashSet<>();
            for (GiftConfigDO current = gift; current != null; current = byId.get(current.getParentId())) {
                if (!seen.add(current.getId()) || current.getName() == null) throw exception(SALES_ORDER_GIFT_SELECTION_INVALID);
                path.add(current.getName());
            }
            Collections.reverse(path);
            result.add(new SalesOrderGiftSnapshot(code, gift.getName(), path, now.toString()));
        }
        return List.copyOf(result);
    }
}
