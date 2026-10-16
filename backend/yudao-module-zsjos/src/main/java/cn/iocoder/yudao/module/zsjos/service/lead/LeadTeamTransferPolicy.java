package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/** Keeps education ownership within the current manager's team during reassignment. */
@Service
public class LeadTeamTransferPolicy {
    @Resource private LeadSubmissionIdentityService identityService;
    @Resource private LeadObjectPermissionService permissionService;
    @Resource private cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAssignmentHistoryMapper historyMapper;
    @Resource private cn.iocoder.yudao.module.system.api.permission.PermissionApi permissionApi;

    public boolean isEducation(LeadDO lead) {
        return OWNER_EDUCATION.equals(resolveIdentity(lead));
    }

    public String resolveIdentity(LeadDO lead) {
        if (!ASSIGNMENT_RECYCLE_PENDING.equals(lead.getAssignmentStatus())) {
            if (known(lead.getOwnerIdentity())) return lead.getOwnerIdentity();
            throw exception(LEAD_RECYCLED_IDENTITY_UNKNOWN);
        }
        Long source = lead.getRecycleSourceOwnerUserId();
        if (source == null || lead.getOwnerUserId() != null) throw exception(LEAD_RECYCLED_IDENTITY_UNKNOWN);
        var rows = historyMapper.selectByLeadId(lead.getId());
        // The latest transition must prove this recycle. Never search past a newer release,
        // recycle or ownership transition, even when an older snapshot names the same employee.
        if (rows.isEmpty()) throw exception(LEAD_RECYCLED_IDENTITY_UNKNOWN);
        var recycle = rows.getFirst();
        if (!ACTION_RECYCLE.equals(recycle.getActionType())
                || !source.equals(recycle.getFromOwnerUserId()) || recycle.getToOwnerUserId() != null)
            throw exception(LEAD_RECYCLED_IDENTITY_UNKNOWN);
        String evidence = recycle.getOwnerIdentitySnapshot();
        if (!known(evidence)) {
            if (evidence != null || rows.size() < 2) throw exception(LEAD_RECYCLED_IDENTITY_UNKNOWN);
            var owner = rows.get(1);
            if (owner.getActionType() == null || !java.util.Set.of(ACTION_ACCEPT, ACTION_CLAIM, ACTION_TRANSFER).contains(owner.getActionType())
                    || !source.equals(owner.getToOwnerUserId()) || !known(owner.getOwnerIdentitySnapshot()))
                throw exception(LEAD_RECYCLED_IDENTITY_UNKNOWN);
            evidence = owner.getOwnerIdentitySnapshot();
        }
        if (lead.getOwnerIdentity() != null && !evidence.equals(lead.getOwnerIdentity()))
            throw exception(LEAD_RECYCLED_IDENTITY_UNKNOWN);
        return evidence;
    }

    private static boolean known(String identity) {
        return OWNER_EDUCATION.equals(identity) || OWNER_SALES.equals(identity);
    }

    public void requireSupportedSource(LeadDO lead) {
        if (!EducationOwnershipPolicy.supports(lead)) throw exception(SUBORDINATE_SALES_TARGET_INVALID);
    }

    public boolean eligibleTarget(Long target, Long manager) {
        return target != null && permissionService.getManagedUserIds(manager).contains(target)
                && identityService.isEligibleEducationOwner(target)
                && permissionApi.hasAnyPermissions(target, "zsjos:lead-follow-up:create")
                && permissionApi.hasAnyPermissions(target, "zsjos:lead:qualify")
                && permissionApi.hasAnyPermissions(target, "zsjos:sales-order:create");
    }
    public java.util.Set<Long> eligibleTargets(java.util.List<cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO> users) {
        return identityService.getEligibleEducationOwners(users).stream()
                .filter(id -> permissionApi.hasAnyPermissions(id,"zsjos:lead-follow-up:create")
                        && permissionApi.hasAnyPermissions(id,"zsjos:lead:qualify")
                        && permissionApi.hasAnyPermissions(id,"zsjos:sales-order:create"))
                .collect(java.util.stream.Collectors.toSet());
    }

    public void requireTarget(LeadDO lead, Long target, Long manager) {
        requireSupportedSource(lead);
        if (!isEducation(lead) || !eligibleTarget(target, manager)) {
            throw exception(SUBORDINATE_SALES_TARGET_INVALID);
        }
    }
}
