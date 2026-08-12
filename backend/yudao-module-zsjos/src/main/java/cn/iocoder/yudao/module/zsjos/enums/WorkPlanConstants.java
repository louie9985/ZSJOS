package cn.iocoder.yudao.module.zsjos.enums;

import java.util.Set;

public interface WorkPlanConstants {
    String BIZ_TYPE_WORK_PLAN = "work_plan";
    String BIZ_TYPE_WORK_TASK = "work_task";

    String PERIOD_MONTH = "month";
    String PERIOD_WEEK = "week";
    String PERIOD_DAY = "day";
    String PERIOD_QUARTER = "quarter";
    String PERIOD_YEAR = "year";
    String PERIOD_CUSTOM = "custom";
    Set<String> PERIOD_TYPES = Set.of(PERIOD_DAY, PERIOD_WEEK, PERIOD_MONTH, PERIOD_QUARTER, PERIOD_YEAR, PERIOD_CUSTOM);

    String SECTION_PLAN = "plan";
    String SECTION_TASK = "task";
    String SECTION_REPORT = "report";
    String SECTION_SUMMARY = "summary";
    Set<String> FIELD_SECTIONS = Set.of(SECTION_PLAN, SECTION_TASK, SECTION_REPORT, SECTION_SUMMARY);

    String PLAN_DRAFT = "draft";
    String PLAN_ACTIVE = "active";
    String PLAN_COMPLETED = "completed";
    String PLAN_CANCELLED = "cancelled";

    String TASK_DRAFT = "draft";
    String TASK_PENDING = "pending";
    String TASK_AWAITING_CONFIRMATION = "awaiting_confirmation";
    String TASK_COMPLETED = "completed";
    String TASK_CANCELLED = "cancelled";

    String CONFIRM_AUTO_APPROVED = "auto_approved";
    String CONFIRM_APPROVED = "approved";
    String CONFIRM_REJECTED = "rejected";

    String SUBJECT_PLAN = "plan";
    String SUBJECT_TASK = "task";
    String SUBJECT_REPORT = "report";
    String SUBJECT_SUMMARY = "summary";

    String FIELD_ORIGIN_TEMPLATE = "template";
    String FIELD_ORIGIN_SUPPLEMENTAL = "supplemental";

    String TASK_TYPE_WORK_TASK = "work_task_execute";
    String TASK_TYPE_WORK_CONFIRM = "work_task_confirm";
    String TASK_TYPE_PLAN_SUMMARY = "work_plan_summary";
    String ACTION_OPEN_WORK_TASK = "OPEN_WORK_TASK";
    String ACTION_CONFIRM_WORK_TASK = "CONFIRM_WORK_TASK";
    String ACTION_SUMMARIZE_WORK_PLAN = "SUMMARIZE_WORK_PLAN";

    String PERMISSION_QUERY = "zsjos:work-plan:query";
    String PERMISSION_CREATE = "zsjos:work-plan:create";
    String PERMISSION_UPDATE = "zsjos:work-plan:update";
    String PERMISSION_PUBLISH = "zsjos:work-plan:publish";
    String PERMISSION_ASSIGN = "zsjos:work-plan:assign";
    String PERMISSION_COMPLETE = "zsjos:work-plan:complete";
    String PERMISSION_REVIEW = "zsjos:work-plan:review";
    String PERMISSION_CANCEL = "zsjos:work-plan:cancel";
    String PERMISSION_CLOSE = "zsjos:work-plan:close";
    String PERMISSION_DECOMPOSE = "zsjos:work-plan:decompose";
    String PERMISSION_EXPORT = "zsjos:work-plan:export";
    String PERMISSION_CONFIG_QUERY = "zsjos:work-plan-config:query";
    String PERMISSION_CONFIG_CREATE = "zsjos:work-plan-config:create";
    String PERMISSION_CONFIG_UPDATE = "zsjos:work-plan-config:update";
    String PERMISSION_CONFIG_PUBLISH = "zsjos:work-plan-config:publish";
    String PERMISSION_CONFIG_DISABLE = "zsjos:work-plan-config:disable";
}
