package cn.iocoder.yudao.module.zsjos.service.account;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Dates stay anchored to the original round even when a form is submitted early. */
public final class WeeklyDiagnosisPeriods {
    private WeeklyDiagnosisPeriods() {}
    public static int week(LocalDateTime anchor, LocalDateTime due) {
        long days=ChronoUnit.DAYS.between(anchor.toLocalDate(),due.toLocalDate());
        if(days<=0 || days%7!=0) throw new IllegalArgumentException("Invalid diagnosis deadline");
        return Math.toIntExact(days/7);
    }
    public static List<Integer> intervals(int week) {
        if(week<1) throw new IllegalArgumentException("Invalid diagnosis week");
        return week%4==0?List.of(7,14,28):week%2==0?List.of(7,14):List.of(7);
    }
    public static String key(Long account,String round,LocalDateTime due) {
        return account+":"+round+":"+due.toLocalDate();
    }
}
