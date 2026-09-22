package cn.iocoder.yudao.module.zsjos.service.performance;
import cn.iocoder.yudao.module.zsjos.controller.admin.performance.vo.PerformanceVO.CalendarDay;
import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.PerformanceFact;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
public final class PerformanceCalendar {
 private PerformanceCalendar() {}
 public static CalendarDay summarize(LocalDate day,List<PerformanceFact> receipts,List<PerformanceFact> tasks,LocalDateTime now){
  var received=receipts.stream().filter(x->x.getReceivedAt().toLocalDate().equals(day)).collect(Collectors.toMap(PerformanceFact::getLeadId,Function.identity(),(a,b)->a)).values();
  long valid=0,invalid=0,pending=0,overdue=0,ended=0,late=0,onTime=0,due=0,unknown=0;
  for(var receipt:received){
   var rounds=tasks.stream().filter(t->"lead_qualification".equals(t.getGroupKey())&&Objects.equals(t.getLeadId(),receipt.getLeadId())&&Objects.equals(t.getUserId(),receipt.getUserId())&&Objects.equals(t.getAssignmentId(),receipt.getAssignmentId())).toList();
   var task=rounds.stream().max(Comparator.comparing(PerformanceFact::getId)).orElse(null);
   var originalDeadline=rounds.stream().map(PerformanceFact::getDueAt).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
   if(task==null||task.getOutcome()==null){unknown++;continue;}
   boolean judged=Set.of("valid","invalid").contains(task.getOutcome());
   boolean completedCycle=judged||"ended".equals(task.getOutcome())||Boolean.FALSE.equals(task.getCurrentAssignment());
   // A cancelled responsibility is not a successful qualification. Keep its past deadline in the timeliness denominator.
   if(originalDeadline!=null&&(!originalDeadline.isAfter(now)||judged)&&(!completedCycle||judged||task.getEndedAt()!=null&&!task.getEndedAt().isBefore(originalDeadline)))due++;
   if(judged){if("invalid".equals(task.getOutcome()))invalid++;else valid++;if(task.getCompletedAt()!=null&&originalDeadline!=null){if(task.getCompletedAt().isAfter(originalDeadline))late++;else onTime++;}}
   else if(completedCycle)ended++;
   else if("overdue".equals(task.getOutcome())||task.getDueAt()!=null&&!task.getDueAt().isAfter(now))overdue++;
   else pending++;
  }
  return new CalendarDay(day,received.size(),valid,invalid,pending,overdue,ended,late,onTime,due,unknown);
 }
}
