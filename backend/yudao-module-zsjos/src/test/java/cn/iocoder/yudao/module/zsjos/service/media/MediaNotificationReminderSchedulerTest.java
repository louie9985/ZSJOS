package cn.iocoder.yudao.module.zsjos.service.media;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyTimingRuleRespDTO;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
class MediaNotificationReminderSchedulerTest {
    @Test void usesRuleOffsetAndDoesNotSendStaleAdvanceReminders() {
        var anchor=LocalDateTime.of(2026,9,20,10,0);
        var rule=new NotifyTimingRuleRespDTO(1L,"scene","advance",60);
        assertFalse(MediaNotificationReminderScheduler.due(anchor,rule,anchor.minusMinutes(61)));
        assertTrue(MediaNotificationReminderScheduler.due(anchor,rule,anchor.minusMinutes(60)));
        assertFalse(MediaNotificationReminderScheduler.due(anchor,rule,anchor));
        rule.setTimingStage("overdue");rule.setTimingOffsetMinutes(120);
        assertFalse(MediaNotificationReminderScheduler.due(anchor,rule,anchor.plusMinutes(119)));
        assertTrue(MediaNotificationReminderScheduler.due(anchor,rule,anchor.plusMinutes(120)));
    }
}
