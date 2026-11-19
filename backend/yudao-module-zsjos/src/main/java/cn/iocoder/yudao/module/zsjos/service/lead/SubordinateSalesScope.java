package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.system.api.dept.PostApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.util.*;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/** Sales membership and persisted ownership are cumulative boundaries, even for tenant-wide readers. */
@Service
public class SubordinateSalesScope {
    @Resource private AdminUserApi users;
    @Resource private PermissionApi permissions;
    @Resource private LeadObjectPermissionService managers;
    @Resource private LeadTeamTransferPolicy ownership;
    @Resource private cn.iocoder.yudao.module.zsjos.service.sales.SalesDepartmentBoundaryService salesBoundary;

    public List<AdminUserRespDTO> readable(Long reader) {
        if (!permissions.hasTenantReadAllAccess(reader)) return managed(reader);
        var all = new ArrayList<>(users.getUserListByStatus(0));
        all.addAll(users.getUserListByStatus(1));
        return sales(reader, all);
    }
    public List<AdminUserRespDTO> managed(Long manager) {
        var ids = managers.getManagedUserIds(manager);
        return ids.isEmpty() ? List.of() : sales(manager, users.getUserList(ids));
    }
    /**
     * Managed subordinates that may become a new owner. Only the sales department boundary applies; the manager's
     * System data scope governs reading, not choosing a same-department subordinate as the new owner.
     */
    public Set<Long> transferTargetIds(Long manager) {
        var ids = managers.getManagedUserIds(manager);
        return ids.isEmpty() ? Set.of() : salesBoundary.filterTransferTargets(manager, ids);
    }
    private List<AdminUserRespDTO> sales(Long actor, List<AdminUserRespDTO> candidates) {
        Set<Long> allowed = salesBoundary.accessibleSalesUserIds(actor);
        return candidates.stream().filter(u -> allowed.contains(u.getId())).toList();
    }
    public boolean salesLead(LeadDO lead) {
        return lead != null && OWNER_SALES.equals(ownership.resolveIdentity(lead));
    }
    public void requireManagedLead(LeadDO lead, Long manager) {
        requireManagedLead(lead, managedIds(manager));
    }
    /** Batch callers resolve {@link #managedIds} once and check each lead in memory. */
    public void requireManagedLead(LeadDO lead, Set<Long> managedIds) {
        if (lead == null) throw exception(LEAD_NOT_EXISTS);
        Long owner = lead.getOwnerUserId() == null ? lead.getRecycleSourceOwnerUserId() : lead.getOwnerUserId();
        if (!salesLead(lead) || owner == null || !managedIds.contains(owner))
            throw exception(SUBORDINATE_LEAD_OWNER_CHANGED);
    }
    public Set<Long> managedIds(Long manager) {
        Set<Long> ids = new HashSet<>();
        managed(manager).forEach(user -> ids.add(user.getId()));
        return ids;
    }
}
