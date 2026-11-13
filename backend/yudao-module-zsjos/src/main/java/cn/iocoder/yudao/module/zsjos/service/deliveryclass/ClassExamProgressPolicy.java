package cn.iocoder.yudao.module.zsjos.service.deliveryclass;

import java.util.List;

/** Examination progress is independent of service closure, task execution and transfer eligibility. */
public final class ClassExamProgressPolicy {
    private ClassExamProgressPolicy() {}
    public static final String STUDY = "STUDY", SPRINT = "SPRINT", EXAM = "EXAM", ENDED = "ENDED";
    public static String target(String current, String action) {
        return switch (action) {
            case "SPRINT" -> STUDY.equals(current) ? SPRINT : null;
            case "EXAM" -> SPRINT.equals(current) ? EXAM : null;
            case "END" -> EXAM.equals(current) ? ENDED : null;
            case "BACK" -> switch (current) { case SPRINT -> STUDY; case EXAM -> SPRINT; case ENDED -> EXAM; default -> null; };
            default -> null;
        };
    }
    public static List<String> actions(String current) {
        return switch (current) {
            case STUDY -> List.of("SPRINT");
            case SPRINT -> List.of("EXAM", "BACK");
            case EXAM -> List.of("END", "BACK");
            case ENDED -> List.of("BACK");
            default -> List.of();
        };
    }
    public static String label(String stage) {
        return switch (stage) { case STUDY -> "常规学习"; case SPRINT -> "考前冲刺"; case EXAM -> "完成考试"; case ENDED -> "结束服务"; default -> "阶段未记录"; };
    }
}
