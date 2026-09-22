package cn.iocoder.yudao.module.zsjos.service.performance;
import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.PerformanceFact;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class PerformanceCalendarTest {
 final LocalDateTime received=LocalDateTime.of(2026,9,1,10,0);
 PerformanceFact receipt(){var r=new PerformanceFact();r.setLeadId(1L);r.setUserId(2L);r.setAssignmentId(3L);r.setReceivedAt(received);return r;}
 PerformanceFact task(long id,String outcome){var t=receipt();t.setId(id);t.setGroupKey("lead_qualification");t.setOutcome(outcome);t.setDueAt(received.plusDays(3));t.setCurrentAssignment(true);return t;}
 @Test void timeoutCancellationRemainsRed(){var t=task(1,"overdue");t.setStatus("cancelled");var d=PerformanceCalendar.summarize(received.toLocalDate(),List.of(receipt()),List.of(t),received.plusDays(4));assertEquals(1,d.overdue());assertEquals(0,d.ended());}
 @Test void invalidCancellationIsNotTransfer(){var t=task(1,"invalid");t.setStatus("cancelled");t.setCompletedAt(received.plusDays(1));var d=PerformanceCalendar.summarize(received.toLocalDate(),List.of(receipt()),List.of(t),received.plusDays(2));assertEquals(1,d.invalid());assertEquals(1,d.onTime());assertEquals(1,d.dueCount());}
 @Test void restoredLateCompletionRetainsOriginalDeadline(){var a=task(1,"overdue");var b=task(2,"valid");b.setDueAt(received.plusDays(6));b.setCompletedAt(received.plusDays(5));var d=PerformanceCalendar.summarize(received.toLocalDate(),List.of(receipt()),List.of(a,b),received.plusDays(7));assertEquals(1,d.valid());assertEquals(1,d.lateCompleted());assertEquals(0,d.onTime());}
 @Test void transferEndsOldResponsibility(){var t=task(1,"pending");t.setCurrentAssignment(false);t.setEndedAt(received.plusDays(1));var d=PerformanceCalendar.summarize(received.toLocalDate(),List.of(receipt()),List.of(t),received.plusDays(5));assertEquals(1,d.ended());assertEquals(0,d.overdue());assertEquals(0,d.dueCount());}
 @Test void missingHistoryNeverInventsSuccessfulClosure(){var d=PerformanceCalendar.summarize(received.toLocalDate(),List.of(receipt()),List.of(),received.plusDays(5));assertEquals(1,d.unknown());assertEquals(0,d.valid());assertEquals(0,d.ended());}
 @Test void teamSameDayReceiptsDeduplicate(){var d=PerformanceCalendar.summarize(received.toLocalDate(),List.of(receipt(),receipt()),List.of(task(1,"pending")),received.plusDays(1));assertEquals(1,d.received());assertEquals(1,d.pending());}
}
