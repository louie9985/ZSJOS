package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

/** Mirrors LeadTeamTransferPolicy's latest-transition proof; unknown recycled ownership fails closed. */
public final class SubordinateSalesIdentitySql {
    private SubordinateSalesIdentitySql() {}
    public static final String CONDITION = "((COALESCE(zsjos_lead.assignment_status,'')<>'recycle_pending' AND zsjos_lead.owner_identity='sales') OR ("
            + "zsjos_lead.assignment_status='recycle_pending' AND zsjos_lead.owner_user_id IS NULL "
            + "AND (zsjos_lead.owner_identity IS NULL OR zsjos_lead.owner_identity='sales') AND EXISTS ("
            + "SELECT 1 FROM zsjos_lead_assignment_history h WHERE h.tenant_id=zsjos_lead.tenant_id AND h.deleted=0 "
            + "AND h.lead_id=zsjos_lead.id AND h.id=(SELECT x.id FROM zsjos_lead_assignment_history x "
            + "WHERE x.tenant_id=zsjos_lead.tenant_id AND x.deleted=0 AND x.lead_id=zsjos_lead.id ORDER BY x.occurred_at DESC,x.id DESC LIMIT 1) "
            + "AND h.action_type='recycle' AND h.from_owner_user_id=zsjos_lead.recycle_source_owner_user_id AND h.to_owner_user_id IS NULL "
            + "AND (h.owner_identity_snapshot='sales' OR (h.owner_identity_snapshot IS NULL AND EXISTS ("
            + "SELECT 1 FROM zsjos_lead_assignment_history p WHERE p.tenant_id=h.tenant_id AND p.deleted=0 AND p.lead_id=h.lead_id "
            + "AND p.id=(SELECT x.id FROM zsjos_lead_assignment_history x WHERE x.tenant_id=h.tenant_id AND x.deleted=0 "
            + "AND x.lead_id=h.lead_id ORDER BY x.occurred_at DESC,x.id DESC LIMIT 1 OFFSET 1) "
            + "AND p.action_type IN ('accept','claim','transfer') AND p.to_owner_user_id=h.from_owner_user_id AND p.owner_identity_snapshot='sales'))))))";
}
