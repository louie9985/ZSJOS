package cn.iocoder.yudao.module.zsjos.service.studentcontact;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO;
import java.util.*;
import static cn.iocoder.yudao.module.zsjos.service.studentcontact.StudentContactConstants.*;

public final class StudentContactReadPolicy {
    private StudentContactReadPolicy() {}
    public static final List<String> TASK_TYPES = List.of(TYPE_FIRST_CONTACT, TYPE_STUDY_PLAN, TYPE_CONTACT);
    public static BusinessTaskDO currentTask(Collection<BusinessTaskDO> tasks) {
        return tasks.stream().filter(t -> "pending".equals(t.getStatus()) && TASK_TYPES.contains(t.getTaskType()))
                .min(Comparator.comparingInt((BusinessTaskDO t) -> TASK_TYPES.indexOf(t.getTaskType()))
                        .thenComparing(BusinessTaskDO::getId, Comparator.reverseOrder())).orElse(null);
    }
    public static String stageLabel(String code) {
        if (code == null || code.isBlank()) code = STAGE_FIRST_CONTACT;
        return switch (code) {
            case STAGE_FIRST_CONTACT -> "首联";
            case STAGE_STUDY_PLAN -> "制定学习计划";
            case STAGE_SUPERVISION -> "常规督学";
            case STAGE_EXAM_PREPARATION -> "考前通知与冲刺";
            case STAGE_POST_EXAM -> "考后回访";
            case STAGE_RESULT -> "成绩通知";
            case STAGE_CERTIFICATE -> "证书通知与邮寄";
            case STAGE_CONTINUOUS_FOLLOW_UP -> "持续回访";
            case STAGE_COMPLETED -> "服务完成";
            default -> "未知阶段";
        };
    }
}
