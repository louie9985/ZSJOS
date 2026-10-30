package cn.iocoder.yudao.module.zsjos.service.advancedfilter;

import java.util.Map;
import static cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterFields.*;

/** Current-cycle fields must never match a closed historical public-sea cycle. */
final class PublicSeaFilterFields {
    private PublicSeaFilterFields() {}

    static Map<String, Binding> current(String column) {
        return bind("lead", "(SELECT psc." + column
                + " FROM zsjos_lead_public_sea_pool_cycle psc WHERE psc.lead_id=l.id"
                + " AND psc.tenant_id=l.tenant_id AND psc.deleted=b'0'"
                + " AND psc.status IN ('waiting_assignment','assigned','deal_pending')"
                + " ORDER BY psc.id DESC LIMIT 1)", null);
    }

    static void register(Map<String, Field> result) {
        add(result, selectSource(Sensitivity.PERSONAL, "publicSea.originalOwnerUserId", PEOPLE, "公海原负责人 A", "visible-users", current("original_owner_user_id")));
        add(result, selectSource(Sensitivity.PERSONAL, "publicSea.collaboratorUserId", PEOPLE, "公海协同销售 B", "visible-users", current("collaborator_user_id")));
        add(result, selectSource(Sensitivity.PERSONAL, "publicSea.frozenDeptId", PEOPLE, "公海归属部门", "visible-departments", current("frozen_dept_id")));
        add(result, number(Sensitivity.STANDARD, "publicSea.cycleNo", EXTRA, "公海轮次", current("cycle_no")));
        add(result, date(Sensitivity.STANDARD, "publicSea.ownershipStartedAt", TIME, "公海归属开始时间", current("ownership_started_at")));
        add(result, date(Sensitivity.STANDARD, "publicSea.enteredAt", TIME, "进入公海时间", current("entered_at")));
        add(result, date(Sensitivity.STANDARD, "publicSea.assignedAt", TIME, "公海协同指派时间", current("assigned_at")));
        add(result, date(Sensitivity.STANDARD, "publicSea.dueAt", TIME, "公海到期时间", current("due_at")));
    }
}
