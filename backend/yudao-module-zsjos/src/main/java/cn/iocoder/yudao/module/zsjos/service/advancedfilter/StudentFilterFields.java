package cn.iocoder.yudao.module.zsjos.service.advancedfilter;

import java.util.*;

import static cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterFields.*;
import static cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterRelations.*;

final class StudentFilterFields {
    private StudentFilterFields() {}

    static void register(Map<String, Field> result) {
        String scene = "student_service";
        add(result, date(Sensitivity.STANDARD, "service.examDate", TIME, "考试日期", bind(scene, "sr.exam_date", null)));
        add(result, date(Sensitivity.STANDARD, "service.acceptedAt", TIME, "服务接收时间", bind(scene, "sr.accepted_at", null)));
        add(result, date(Sensitivity.STANDARD, "service.latestContactAt", TIME, "最近联系时间", bind(scene, cn.iocoder.yudao.module.zsjos.dal.mysql.registration.StudentServiceSummarySql.LAST_CONTACT, null)));
        add(result, date(Sensitivity.STANDARD, "service.taskDueAt", TIME, "当前联系任务截止时间", bind(scene, cn.iocoder.yudao.module.zsjos.dal.mysql.registration.StudentServiceSummarySql.task("due_at"), null)));
        add(result, select(Sensitivity.STANDARD, "service.acceptanceStatus", STATUS, "服务接收状态", options("pending", "待接收", "accepted", "已接收"), bind(scene, "sr.acceptance_status", null)));
        add(result, select(Sensitivity.STANDARD, "service.deliveryStage", STATUS, "交付阶段", options("first_contact", "首联", "study_plan", "制定学习计划", "supervision", "常规督学", "exam_preparation", "考前通知与冲刺", "post_exam", "考后回访", "result", "成绩通知", "certificate", "证书通知与邮寄", "continuous_follow_up", "持续回访", "completed", "服务完成"), bind(scene, "COALESCE(NULLIF(sr.delivery_stage,''),'first_contact')", null)));
        add(result, select(Sensitivity.STANDARD, "service.taskState", STATUS, "联系任务状态", options("none", "暂无任务", "pending", "待处理", "overdue", "已逾期"), bind(scene, cn.iocoder.yudao.module.zsjos.dal.mysql.registration.StudentServiceSummarySql.TASK_STATE, null)));
        add(result, selectSource(Sensitivity.PERSONAL, "service.contentDirectorUserId", PEOPLE, "编导", "visible-users", bind(scene, "sr.content_director_user_id", null)));
        add(result, selectSource(Sensitivity.PERSONAL, "service.careerPlannerUserId", PEOPLE, "职业规划师", "visible-users", bind(scene, "sr.career_planner_user_id", null)));
        add(result, selectSource(Sensitivity.PERSONAL, "service.operatorUserId", PEOPLE, "运营负责人", "visible-users", bind(scene, "sr.operator_user_id", null)));
        add(result, select(Sensitivity.STANDARD, "service.status", STATUS, "服务状态", options("active", "服务中", "paused", "已暂停", "completed", "已完成", "terminated", "已终止", "closed", "已关闭"), bind("student", cn.iocoder.yudao.module.zsjos.service.registration.TeachingServiceLifecycle.STATUS_SQL, serviceFromStudent)));
        add(result, selectSource(Sensitivity.PERSONAL, "service.ownerUserId", PEOPLE, "服务负责人", "visible-users", bind("student", "sr.owner_user_id", serviceFromStudent)));
        add(result, date(Sensitivity.STANDARD, "service.activatedAt", TIME, "服务激活时间", bind("student", "sr.activated_at", serviceFromStudent)));
        add(result, date(Sensitivity.STANDARD, "service.pausedAt", TIME, "服务暂停时间", bind("student", "sr.paused_at", serviceFromStudent)));
        add(result, date(Sensitivity.STANDARD, "service.completedAt", TIME, "服务完成时间", bind("student", "sr.completed_at", serviceFromStudent)));
        add(result, date(Sensitivity.STANDARD, "service.terminatedAt", TIME, "服务终止时间", bind("student", "sr.terminated_at", serviceFromStudent)));
        add(result, text(Sensitivity.FREE_TEXT, "service.pauseReason", EXTRA, "暂停原因", bind("student", "sr.pause_reason", serviceFromStudent)));
        add(result, text(Sensitivity.FREE_TEXT, "service.terminationReason", EXTRA, "终止原因", bind("student", "sr.termination_reason", serviceFromStudent)));
    }

}
