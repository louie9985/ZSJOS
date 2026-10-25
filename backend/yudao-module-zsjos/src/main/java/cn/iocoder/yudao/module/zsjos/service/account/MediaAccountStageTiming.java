package cn.iocoder.yudao.module.zsjos.service.account;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Account stage timing is independent of diagnosis frequency and review processing time. */
public final class MediaAccountStageTiming {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    public static final String RECOVERY_KEY = "_stageTimingRecovery";
    private MediaAccountStageTiming() {}

    public static LocalDateTime enteredAt(MediaAccountDO account, String stage, LocalDateTime now) {
        return Objects.equals(account.getSStage(), stage) ? account.getSStageEnteredAt() : now;
    }

    public static Map<String, Object> snapshot(MediaAccountDO account, LocalDateTime asOf) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sStageDurationAsOf", millis(asOf));
        LocalDateTime entered = account.getSStageEnteredAt();
        if (account.getSStage() == null || account.getSStage().isBlank() || entered == null || entered.isAfter(asOf)) return result;
        result.put("sStageEnteredAt", millis(entered));
        result.put("sStageDurationDays", ChronoUnit.DAYS.between(entered.toLocalDate(), asOf.toLocalDate()));
        String source = "RECORDED";
        if (account.getDetailValuesJson() != null) {
            Map<?, ?> values = JsonUtils.parseObject(account.getDetailValuesJson(), Map.class);
            if (values != null && values.get(RECOVERY_KEY) instanceof Map<?, ?> recovery
                    && Objects.equals(account.getSStage(), recovery.get("stage"))
                    && recovery.get("enteredAt") instanceof Number time && time.longValue() == millis(entered)) {
                source = "HISTORY_RECONSTRUCTED";
            }
        }
        result.put("sStageTimingSource", source);
        return result;
    }

    public static long millis(LocalDateTime value) { return value.atZone(ZONE).toInstant().toEpochMilli(); }
}
