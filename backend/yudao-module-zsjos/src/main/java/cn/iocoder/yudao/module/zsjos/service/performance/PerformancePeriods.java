package cn.iocoder.yudao.module.zsjos.service.performance;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.math.*;
public final class PerformancePeriods {
 public static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
 public record Window(String key,String label,LocalDateTime start,LocalDateTime end) {
  public boolean contains(LocalDateTime value) {return value!=null&&!value.isBefore(start)&&value.isBefore(end);}
 }
 private PerformancePeriods() {}
 public static Window window(String key,LocalDateTime now) {
  LocalDate d=now.toLocalDate(), start; LocalDateTime end=now;
  String label;
  switch(key) {
   case "today": start=d; label="今日"; break;
   case "week": start=d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); label="本周"; break;
   case "lastWeek": start=d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1); end=start.plusWeeks(1).atStartOfDay(); label="上周"; break;
   case "month": start=d.withDayOfMonth(1); label="本月"; break;
   case "lastMonth": start=d.withDayOfMonth(1).minusMonths(1); end=start.plusMonths(1).atStartOfDay(); label="上月"; break;
   case "quarter": start=LocalDate.of(d.getYear(),((d.getMonthValue()-1)/3)*3+1,1); label="本季度"; break;
   case "year": start=d.withDayOfYear(1); label="本年"; break;
   default: int n=Integer.parseInt(key.substring(4)); if(!java.util.Set.of(7,30,60,90).contains(n))throw new IllegalArgumentException("周期无效"); start=d.minusDays(n-1); label="近"+n+"日";
  }
  return new Window(key,label,start.atStartOfDay(),end);
 }
 public static BigDecimal ratio(BigDecimal a,BigDecimal b) {return b==null||b.signum()==0?null:a.divide(b,6,RoundingMode.HALF_UP);}
 public static boolean eligibleAverage(BigDecimal a) {return a!=null&&a.compareTo(BigDecimal.ZERO)!=0&&a.compareTo(new BigDecimal("0.01"))!=0;}
 public static boolean withinValidity(LocalDateTime received,LocalDateTime submitted) {return received!=null&&submitted!=null&&!submitted.isBefore(received)&&submitted.isBefore(received.plusDays(60));}
}
