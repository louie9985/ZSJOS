package cn.iocoder.yudao.module.zsjos.service.performance;
import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.PerformanceFact;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class AutomaticQualificationCalendarTest {
 @org.junit.jupiter.params.ParameterizedTest
 @org.junit.jupiter.params.provider.ValueSource(strings={"sales_self_sourced_auto","education_self_sourced_auto"})
 void automaticValidityCountsButTimingDoesNotAndLaterManualRoundCountsNormally(String generationSource){
  var now=LocalDateTime.of(2026,9,29,12,0);var received=new PerformanceFact();received.setLeadId(1L);received.setUserId(2L);received.setAssignmentId(3L);received.setReceivedAt(now.minusDays(1));
  var automatic=round(1L,now.minusDays(1),now.minusDays(1));automatic.setGenerationSource(generationSource);
  var day=PerformanceCalendar.summarize(now.toLocalDate().minusDays(1),List.of(received),List.of(automatic),now);
  assertEquals(1,day.received());assertEquals(1,day.valid());assertEquals(0,day.unknown());assertEquals(0,day.dueCount());assertEquals(0,day.onTime());assertEquals(0,day.lateCompleted());
  var manual=round(2L,now.plusHours(1),now);var changed=PerformanceCalendar.summarize(now.toLocalDate().minusDays(1),List.of(received),List.of(automatic,manual),now);
  assertEquals(1,changed.valid());assertEquals(1,changed.dueCount());assertEquals(1,changed.onTime());assertEquals(0,changed.lateCompleted());
 }
 private PerformanceFact round(long id,LocalDateTime deadline,LocalDateTime completed){var t=new PerformanceFact();t.setId(id);t.setLeadId(1L);t.setUserId(2L);t.setAssignmentId(3L);t.setGroupKey("lead_qualification");t.setOutcome("valid");t.setDueAt(deadline);t.setCompletedAt(completed);return t;}
}
