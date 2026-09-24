package cn.iocoder.yudao.module.zsjos.service.advancedfilter;

import java.util.*;

import static cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterFields.*;
import static cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterRelations.*;

final class StudentFilterFields {
    private StudentFilterFields() {}

    static void register(Map<String, Field> result) {
        add(result, select(Sensitivity.STANDARD, "service.status", STATUS, "服务状态", options("active", "服务中", "paused", "已暂停", "completed", "已完成", "terminated", "已终止"), bind("student", "sr.status", serviceFromStudent)));
        add(result, selectSource(Sensitivity.PERSONAL, "service.ownerUserId", PEOPLE, "服务负责人", "visible-users", bind("student", "sr.owner_user_id", serviceFromStudent)));
        add(result, date(Sensitivity.STANDARD, "service.activatedAt", TIME, "服务激活时间", bind("student", "sr.activated_at", serviceFromStudent)));
        add(result, date(Sensitivity.STANDARD, "service.pausedAt", TIME, "服务暂停时间", bind("student", "sr.paused_at", serviceFromStudent)));
        add(result, date(Sensitivity.STANDARD, "service.completedAt", TIME, "服务完成时间", bind("student", "sr.completed_at", serviceFromStudent)));
        add(result, date(Sensitivity.STANDARD, "service.terminatedAt", TIME, "服务终止时间", bind("student", "sr.terminated_at", serviceFromStudent)));
        add(result, text(Sensitivity.FREE_TEXT, "service.pauseReason", EXTRA, "暂停原因", bind("student", "sr.pause_reason", serviceFromStudent)));
        add(result, text(Sensitivity.FREE_TEXT, "service.terminationReason", EXTRA, "终止原因", bind("student", "sr.termination_reason", serviceFromStudent)));
    }

}
