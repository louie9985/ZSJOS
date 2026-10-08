package cn.iocoder.yudao.module.zsjos.service.sorting;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.pojo.CursorPageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Collator;
import java.util.*;
import java.util.function.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.invalidParamException;

/** Sort only authorized display projections; never replace a hidden value with its database value. */
@lombok.extern.slf4j.Slf4j
public final class BusinessListSort<T> {
    private static final int BATCH_SIZE = 200;
    private final Map<String, Function<T, ?>> fields = new LinkedHashMap<>();
    private final Function<T, Long> identity;

    public BusinessListSort(Function<T, Long> identity) { this.identity = identity; }
    public BusinessListSort<T> field(String name, Function<T, ?> value) { fields.put(name, value); return this; }

    public boolean requested(String field, String order) {
        if (field == null && order == null) return false;
        if (!fields.containsKey(field) || !("ascend".equals(order) || "descend".equals(order))) {
            throw invalidParamException("排序字段或方向不正确，请重新选择排序");
        }
        return true;
    }

    public Comparator<T> comparator(String field, String order) {
        requested(field, order);
        Collator collator = Collator.getInstance(Locale.CHINA);
        return (left, right) -> {
            int value = compare(fields.get(field).apply(left), fields.get(field).apply(right), order, collator);
            return value != 0 ? value : Long.compare(identity.apply(right), identity.apply(left));
        };
    }

    private static Object normalize(Object value) {
        return value instanceof String text && text.isBlank() ? null : value;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int compare(Object left, Object right, String order, Collator collator) {
        left = normalize(left); right = normalize(right);
        if (left == null || right == null) return left == right ? 0 : left == null ? 1 : -1;
        int result;
        if (left instanceof Number && right instanceof Number) result = new BigDecimal(left.toString()).compareTo(new BigDecimal(right.toString()));
        else if (left instanceof String && right instanceof String) result = collator.compare(left, right);
        else result = ((Comparable) left).compareTo(right);
        return "descend".equals(order) ? -Integer.signum(result) : Integer.signum(result);
    }

    public PageResult<T> page(String field, String order, int pageNo, int pageSize,
                              BiFunction<Integer, Integer, PageResult<T>> load) {
        requested(field, order);
        List<T> rows = collect(load);
        rows.sort(comparator(field, order));
        long offset = (long) (pageNo - 1) * pageSize;
        int start = (int) Math.min(offset, rows.size());
        return new PageResult<>(new ArrayList<>(rows.subList(start, Math.min(start + pageSize, rows.size()))), (long) rows.size());
    }

    private List<T> collect(BiFunction<Integer, Integer, PageResult<T>> load) {
        // Batches bound API/IN-clause size. Deduplicate records if live default ordering changes during the scan.
        long started = System.nanoTime();
        Map<Long, T> rows = new LinkedHashMap<>();
        for (int page = 1; ; page++) {
            PageResult<T> result = load.apply(page, BATCH_SIZE);
            result.getList().forEach(row -> rows.put(identity.apply(row), row));
            if (result.getList().isEmpty() || (long) page * BATCH_SIZE >= result.getTotal()) break;
        }
        log.debug("Business list sorting candidates={} batchSize={} projectionMs={}", rows.size(), BATCH_SIZE, (System.nanoTime() - started) / 1_000_000);
        return new ArrayList<>(rows.values());
    }

    public CursorPageResult<T> cursor(String field, String order, String token, String context, int limit,
                                      BiFunction<Integer, Integer, PageResult<T>> load) {
        requested(field, order);
        Cursor anchor = decode(token, context, field, order);
        List<T> rows = collect(load);
        Function<T, ?> value = fields.get(field);
        rows.sort(comparator(field, order));
        Collator collator = Collator.getInstance(Locale.CHINA);
        if (anchor != null) rows.removeIf(row -> {
            Object current = normalize(value.apply(row));
            Object previous = restore(anchor.value(), current);
            int comparison = compare(current, previous, order, collator);
            return comparison < 0 || comparison == 0 && identity.apply(row) >= anchor.id();
        });
        boolean more = rows.size() > limit;
        List<T> page = new ArrayList<>(rows.subList(0, Math.min(limit, rows.size())));
        String next = null;
        if (more) {
            T last = page.getLast(); Object lastValue = normalize(value.apply(last));
            next = Base64.getUrlEncoder().withoutPadding().encodeToString(JsonUtils.toJsonString(
                    new Cursor(1, context, field, order, identity.apply(last), lastValue == null ? null : lastValue.toString()))
                    .getBytes(StandardCharsets.UTF_8));
        }
        return new CursorPageResult<>(page, next, more);
    }

    private Object restore(String value, Object example) {
        if (value == null) return null;
        // A null candidate still compares after every non-null anchor, regardless of the anchor's type.
        if (example == null) return value;
        try {
            if (example instanceof Number) return new BigDecimal(value);
            if (example instanceof java.time.LocalDateTime) return java.time.LocalDateTime.parse(value);
        } catch (RuntimeException error) { throw invalidParamException("排序游标已失效，请刷新列表"); }
        return value;
    }

    private Cursor decode(String token, String context, String field, String order) {
        if (token == null || token.isBlank()) return null;
        try {
            Cursor cursor = JsonUtils.parseObject(new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8), Cursor.class);
            if (cursor.version() != 1 || cursor.id() == null || !Objects.equals(cursor.context(), context)
                    || !Objects.equals(cursor.field(), field) || !Objects.equals(cursor.order(), order)) throw new IllegalArgumentException();
            return cursor;
        } catch (RuntimeException error) { throw invalidParamException("排序游标已失效，请刷新列表"); }
    }

    public record Cursor(int version, String context, String field, String order, Long id, String value) {}
    public static String first(String... values) { return Arrays.stream(values).filter(v -> v != null && !v.isBlank()).findFirst().orElse(null); }
    public static String join(String delimiter, String... values) { return String.join(delimiter, Arrays.stream(values).filter(v -> v != null && !v.isBlank()).toList()); }
}
