package cn.iocoder.yudao.module.zsjos.service.delivery;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Pure timing rules for the account delivery cycle; persistence and task creation stay in the service layer. */
public final class StudentDeliverySchedule {
    private StudentDeliverySchedule() {}
    public static final Map<String, Integer> DEFAULT_DAYS = Map.of("S0", 3, "S1", 7, "S2", 7, "S3", 14, "S4", 14, "S5", 14, "S6", 30);
    public static final List<String> STAGES = List.of("S0", "S1", "S2", "S3", "S4", "S5", "S6");
    public static final List<String> PARALLEL_AFTER_S2 = List.of("S3", "S4", "S5");
    public static LocalDateTime next(String stage, LocalDateTime base, Map<String, Integer> configured) {
        Integer days = configured.getOrDefault(stage, DEFAULT_DAYS.get(stage));
        if (days == null || days < 0) throw new IllegalArgumentException("Unsupported delivery stage: " + stage);
        return base.plusDays(days);
    }
}
