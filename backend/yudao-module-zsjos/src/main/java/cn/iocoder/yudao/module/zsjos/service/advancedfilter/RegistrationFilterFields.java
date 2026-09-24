package cn.iocoder.yudao.module.zsjos.service.advancedfilter;

import java.util.*;
import cn.iocoder.yudao.module.zsjos.service.registration.RegistrationConstants;

import static cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterFields.*;
import static cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterRelations.*;

final class RegistrationFilterFields {
    private RegistrationFilterFields() {}

    static void register(Map<String, Field> result) {
        add(result, select(Sensitivity.STANDARD, "registration.status", STATUS, "履约状态", options(RegistrationConstants.STATUS_PENDING, "待处理", RegistrationConstants.STATUS_PROCESSING, "处理中", RegistrationConstants.STATUS_COMPLETED, "已完成", RegistrationConstants.STATUS_CANCELLED, "已取消"), bind("registration", "rc.status", null)));
        add(result, selectSource(Sensitivity.PERSONAL, "registration.plannerUserId", PEOPLE, "学习规划师", "visible-users", bind("registration", "rc.study_planner_user_id", null)));
        add(result, date(Sensitivity.STANDARD, "registration.approvedAt", TIME, "报名审核时间", bind("registration", "rc.registration_approved_at", null)));
        add(result, date(Sensitivity.STANDARD, "registration.completedAt", TIME, "履约完成时间", bind("registration", "rc.completed_at", null)));
        add(result, date(Sensitivity.STANDARD, "registration.cancelledAt", TIME, "履约取消时间", bind("registration", "rc.cancelled_at", null)));
        add(result, text(Sensitivity.FREE_TEXT, "registration.cancelReason", EXTRA, "履约取消原因", bind("registration", "rc.cancel_reason", null)));
        String checklist = "SELECT 1 FROM zsjos_registration_case_checklist_item ci LEFT JOIN zsjos_registration_item_attachment cia ON cia.checklist_item_id=ci.id AND cia.deleted=b'0' WHERE ci.registration_case_id=rc.id AND ci.tenant_id=rc.tenant_id AND ci.deleted=b'0'";
        add(result, text(Sensitivity.STANDARD, "registration.checklistTitle", PRODUCT, "清单项目", bind("registration", "ci.title_snapshot", checklist)));
        add(result, select(Sensitivity.STANDARD, "registration.checklistStatus", STATUS, "清单完成状态", options("completed", "已完成", "pending", "未完成"), bind("registration", "CASE WHEN ci.checked=b'1' THEN 'completed' ELSE 'pending' END", checklist)));
        add(result, select(Sensitivity.STANDARD, "registration.hasChecklistAttachment", EXTRA, "清单附件", options("present", "有附件", "absent", "无附件"), bind("registration", "CASE WHEN cia.id IS NULL THEN 'absent' ELSE 'present' END", checklist)));
        String route = "SELECT 1 FROM zsjos_registration_case_route rr WHERE rr.registration_case_id=rc.id AND rr.tenant_id=rc.tenant_id AND rr.deleted=b'0' AND rr.selected=b'1'";
        add(result, text(Sensitivity.STANDARD, "registration.routeDepartment", PRODUCT, "流转部门", bind("registration", "rr.department_name_snapshot", route)));
        add(result, selectSource(Sensitivity.PERSONAL, "registration.routeAssignee", PEOPLE, "流转负责人", "visible-users", bind("registration", "rr.assignee_user_id", route)));
    }

}
