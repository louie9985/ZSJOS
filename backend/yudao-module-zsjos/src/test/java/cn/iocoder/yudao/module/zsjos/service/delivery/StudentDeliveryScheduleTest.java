package cn.iocoder.yudao.module.zsjos.service.delivery;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class StudentDeliveryScheduleTest {
    @Test void defaultsFollowCompletionBasedTimeline() {
        LocalDateTime t = LocalDateTime.of(2026, 1, 1, 9, 0);
        assertEquals(LocalDateTime.of(2026, 1, 4, 9, 0), StudentDeliverySchedule.next("S0", t, Map.of()));
        assertEquals(LocalDateTime.of(2026, 1, 8, 9, 0), StudentDeliverySchedule.next("S1", t, Map.of()));
        assertEquals(LocalDateTime.of(2026, 1, 31, 9, 0), StudentDeliverySchedule.next("S6", t, Map.of()));
    }
    @Test void configuredDaysOverrideDefaults() { assertEquals(LocalDateTime.of(2026, 1, 11, 9, 0), StudentDeliverySchedule.next("S1", LocalDateTime.of(2026,1,1,9,0), Map.of("S1",10))); }
}
