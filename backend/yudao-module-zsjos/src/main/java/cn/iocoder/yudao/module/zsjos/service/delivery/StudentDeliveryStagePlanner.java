package cn.iocoder.yudao.module.zsjos.service.delivery;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** Calculates trigger times without touching persistence; callers persist each result idempotently. */
public final class StudentDeliveryStagePlanner {
    private StudentDeliveryStagePlanner() {}

    public static Map<String, LocalDateTime> initial(LocalDateTime accountOpenedAt, Map<String, Integer> config) {
        Map<String, LocalDateTime> result = new LinkedHashMap<>();
        result.put("S0", StudentDeliverySchedule.next("S0", accountOpenedAt, config));
        return result;
    }

    public static Map<String, LocalDateTime> afterCompletion(String completedStage, LocalDateTime completedAt,
                                                              Map<String, Integer> config) {
        Map<String, LocalDateTime> result = new LinkedHashMap<>();
        switch (completedStage) {
            case "S0" -> result.put("S1", StudentDeliverySchedule.next("S1", completedAt, config));
            case "S1" -> result.put("S2", StudentDeliverySchedule.next("S2", completedAt, config));
            case "S2" -> StudentDeliverySchedule.PARALLEL_AFTER_S2.forEach(stage ->
                    result.put(stage, StudentDeliverySchedule.next(stage, completedAt, config)));
            case "S5" -> result.put("S6", StudentDeliverySchedule.next("S6", completedAt, config));
            default -> { }
        }
        return result;
    }
}
