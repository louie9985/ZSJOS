package cn.iocoder.yudao.module.zsjos.service.contentreview;

import cn.iocoder.yudao.module.zsjos.service.account.MediaAccountStageTiming;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Approval projections use stored account labels, never today's dictionary labels. */
final class ContentReviewAccountProfile {
    static final List<String> FIELDS = List.of("productGoal", "productFormLabel", "publishFrequency", "bottleneckLabel");

    private ContentReviewAccountProfile() {}

    static Map<String, Object> snapshot(MediaAccountDO account) {
        return snapshot(account, LocalDateTime.now(MediaAccountStageTiming.ZONE));
    }

    static Map<String, Object> snapshot(MediaAccountDO account, LocalDateTime asOf) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", account.getId());
        result.put("accountNo", account.getAccountNo());
        result.put("nickname", account.getNickname());
        result.put("platformAccountId", account.getPlatformAccountId());
        result.put("platformValue", account.getPlatformValue());
        result.put("platformLabel", account.getPlatformLabelSnapshot());
        result.put("sStage", account.getSStage());
        result.put("sStageLabel", account.getSStageLabelSnapshot());
        result.put("currentStatusValue", account.getCurrentStatusValue());
        result.put("currentStatusLabel", account.getCurrentStatusLabelSnapshot());
        result.put("ownerOperatorUserId", account.getOwnerOperatorUserId());
        result.put("directorUserId", account.getDirectorUserId());
        result.putAll(MediaAccountStageTiming.snapshot(account, asOf));
        result.putAll(read(account));
        return result;
    }

    static Map<String, Object> read(MediaAccountDO account) {
        Map<?, ?> values = account.getDetailValuesJson() == null ? Map.of()
                : JsonUtils.parseObject(account.getDetailValuesJson(), Map.class);
        List<Map> snapshots = account.getDetailSnapshotJson() == null ? List.of()
                : JsonUtils.parseArray(account.getDetailSnapshotJson(), Map.class);
        Map<String, Object> result = new LinkedHashMap<>();
        put(result, "productGoal", display(values, snapshots, "product_goal", true));
        put(result, "productFormLabel", display(values, snapshots, "work_format", true));
        // A missing dictionary snapshot cannot be replaced with a raw code or an invented historical label.
        put(result, "publishFrequency", display(values, snapshots, "publish_rhythm", false));
        put(result, "bottleneckLabel", account.getPrimaryProblemCodeLabelSnapshot());
        return result;
    }

    static void refresh(Map<String, Object> snapshot, MediaAccountDO account) {
        FIELDS.forEach(snapshot::remove);
        snapshot.remove("profileBackfill");
        snapshot.putAll(read(account));
        // The old maintenance problem list must not substitute for the current diagnosis.
        snapshot.remove("primaryProblems");
    }

    private static String display(Map<?, ?> values, List<Map> snapshots, String key, boolean plainText) {
        if (snapshots != null) for (Map<?, ?> row : snapshots) {
            if (key.equals(row.get("key")) && row.get("displayValue") instanceof String label && !label.isBlank()) return label;
        }
        return plainText && values != null && values.get(key) instanceof String text ? text : null;
    }

    private static void put(Map<String, Object> result, String key, String value) {
        if (value != null && !value.isBlank()) result.put(key, value.trim());
    }
}
