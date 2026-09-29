package cn.iocoder.yudao.module.system.api.notify;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Authoritative catalogue of the personal message-centre categories.
 *
 * <p>This class is the single source of truth for "which category does a message belong to".
 * The SQL filter used by the query paths and the Java classification used by the response
 * mapping are both derived from the same {@link Category} list, so the two cannot drift apart.
 *
 * <p>Membership is mutually exclusive: categories are evaluated in descending priority order
 * and the first match wins. The generated SQL mirrors that by negating every higher-priority
 * predicate. This matters for lead-based scenes such as {@code zsjos.lead.appeal_submitted},
 * which carry both a lead {@code bizType} and an appeal {@code sceneCode}; without exclusion
 * they would surface under two categories at once.
 *
 * <p>Deliberately depends on nothing but plain strings, so both the DAL query builder and the
 * Service response mapping can use it without introducing a package cycle.
 */
public final class NotifyMessageCategory {

    /** 全部分类；不施加任何过滤。 */
    public static final String ALL = "all";
    public static final String APPEAL = "appeal";
    public static final String WITHDRAWAL = "withdrawal";
    public static final String LEAD = "lead";
    /** 兜底分类：未命中任何具名分类的消息。 */
    public static final String SYSTEM = "system";

    /**
     * 按优先级降序排列；先命中者胜出。兜底分类 {@code system} 不在此列，它由其余分类的否定构成。
     */
    private static final List<Category> CATEGORIES = List.of(
            new Category(APPEAL, "申诉", Set.of("appeal", "complaint"), Set.of("appeal", "complaint")),
            new Category(WITHDRAWAL, "提现", Set.of("withdrawal"), Set.of("withdrawal")),
            new Category(LEAD, "客资",
                    Set.of("lead", "sales_order", "student", "student_service", "media-account",
                            "content", "positioning-card", "production-ticket"),
                    Set.of("lead", "registration", "payment")));

    private NotifyMessageCategory() {
    }

    /** 分类目录（含「全部」），顺序即展示顺序。 */
    public static Map<String, String> labels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put(ALL, "全部");
        CATEGORIES.forEach(category -> labels.put(category.key(), category.label()));
        labels.put(SYSTEM, "系统");
        return labels;
    }

    /**
     * 单条消息所属的分类，用于响应体展示。与 {@link #condition(String)} 的判定顺序一致。
     */
    public static String resolve(String bizType, String sceneCode, String sourceEventKey) {
        String normalizedBizType = normalize(bizType);
        String normalizedSceneCode = normalize(sceneCode);
        String normalizedSourceEventKey = normalize(sourceEventKey);
        for (Category category : CATEGORIES) {
            if (category.matches(normalizedBizType, normalizedSceneCode, normalizedSourceEventKey)) {
                return category.key();
            }
        }
        return SYSTEM;
    }

    /**
     * 分类对应的 SQL 条件。
     *
     * <p>空白、{@link #ALL} 或无法识别的分类都不施加过滤 —— 无法识别的取值按「忽略该筛选条件」处理，
     * 而不是静默退化成某一个分类，避免拼写错误把结果集悄悄缩小。
     */
    public static String condition(String category) {
        if (category == null || category.isBlank()) {
            return "1=1";
        }
        String key = normalize(category);
        if (ALL.equals(key)) {
            return "1=1";
        }
        if (SYSTEM.equals(key)) {
            return notAny(CATEGORIES);
        }
        for (int index = 0; index < CATEGORIES.size(); index++) {
            Category current = CATEGORIES.get(index);
            if (!current.key().equals(key)) {
                continue;
            }
            // 排除所有优先级更高的分类，保证一条消息只落在一个分类里。
            String exclusion = notAny(CATEGORIES.subList(0, index));
            return exclusion == null ? match(current) : match(current) + " AND " + exclusion;
        }
        return "1=1";
    }

    /**
     * 不命中任一分类的 SQL 片段。
     *
     * <p>{@code COALESCE} 是必需的：列值为 {@code NULL} 时 {@code IN} 与 {@code LIKE} 都得到
     * {@code NULL} 而不是 {@code FALSE}，直接取反会把历史遗留的全空消息排除掉。
     */
    private static String notAny(List<Category> categories) {
        if (categories.isEmpty()) {
            return null;
        }
        return categories.stream()
                .map(NotifyMessageCategory::match)
                .map(predicate -> "COALESCE(" + predicate + ", 0)")
                .collect(Collectors.joining(" OR ", "NOT (", ")"));
    }

    private static String match(Category category) {
        String inList = category.bizTypes().stream()
                .map(value -> "'" + value + "'")
                .collect(Collectors.joining(","));
        StringBuilder predicate = new StringBuilder("(")
                .append("biz_type IN (").append(inList).append(")");
        category.keywords().forEach(keyword -> predicate
                .append(" OR scene_code LIKE '%").append(keyword).append("%'")
                .append(" OR source_event_key LIKE '%").append(keyword).append("%'"));
        return predicate.append(")").toString();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record Category(String key, String label, Set<String> bizTypes, Set<String> keywords) {

        boolean matches(String bizType, String sceneCode, String sourceEventKey) {
            if (bizTypes.contains(bizType)) {
                return true;
            }
            return keywords.stream()
                    .anyMatch(keyword -> sceneCode.contains(keyword) || sourceEventKey.contains(keyword));
        }
    }
}
