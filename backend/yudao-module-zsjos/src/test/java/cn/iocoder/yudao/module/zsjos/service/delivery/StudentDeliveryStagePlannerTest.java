package cn.iocoder.yudao.module.zsjos.service.delivery;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StudentDeliveryStagePlannerTest {
    private final LocalDateTime base = LocalDateTime.of(2026, 1, 1, 9, 0);
    @Test void s2CreatesThreeIndependentStages() {
        Map<String, LocalDateTime> stages = StudentDeliveryStagePlanner.afterCompletion("S2", base, Map.of());
        assertEquals(3, stages.size());
        assertEquals(LocalDateTime.of(2026, 1, 8, 9, 0), stages.get("S3"));
        assertEquals(base.plusDays(10), stages.get("S4"));
        assertEquals(base.plusDays(14), stages.get("S5"));
    }
    @Test void s5CreatesS6FromItsCompletion() {
        assertEquals(Map.of(), StudentDeliveryStagePlanner.afterCompletion("S5", base, Map.of()));
    }
}
