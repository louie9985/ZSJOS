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
        if (OWNER_EDUCATION.equals(lead.getOwnerIdentity()) || OWNER_SALES.equals(lead.getOwnerIdentity())) return lead.getOwnerIdentity();
        if (!ASSIGNMENT_RECYCLE_PENDING.equals(lead.getAssignmentStatus())) throw exception(LEAD_RECYCLED_IDENTITY_UNKNOWN);
        // Empty recycle snapshots may be legacy. Stop at the latest actual ownership transition;
        // an older owner is never evidence for a newer, unrecorded ownership period.
        for (var row : historyMapper.selectByLeadId(lead.getId())) {
            if (ACTION_RECYCLE.equals(row.getActionType())) {
                if (row.getFromOwnerUserId() != null && !java.util.Objects.equals(row.getFromOwnerUserId(), lead.getRecycleSourceOwnerUserId())) break;
                if (OWNER_EDUCATION.equals(row.getOwnerIdentitySnapshot()) || OWNER_SALES.equals(row.getOwnerIdentitySnapshot())) return row.getOwnerIdentitySnapshot();
                continue;
            }
            if (row.getToOwnerUserId() != null) {
                if (java.util.Objects.equals(row.getToOwnerUserId(), lead.getRecycleSourceOwnerUserId())
                        && (OWNER_EDUCATION.equals(row.getOwnerIdentitySnapshot()) || OWNER_SALES.equals(row.getOwnerIdentitySnapshot()))) return row.getOwnerIdentitySnapshot();
                break;
            }
        }
        throw exception(LEAD_RECYCLED_IDENTITY_UNKNOWN);
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
        boolean source = SOURCE_EDUCATION_SELF.equals(lead.getSourceType())
                || SOURCE_EDUCATION_SELF.equals(lead.getSubmissionSourceType())
                || SOURCE_PARTNER.equals(lead.getSourceType()) && DISPATCH_SPECIFIED.equals(lead.getDispatchMode());
        if (!isEducation(lead) || !source || !eligibleTarget(target, manager)) {
            throw exception(SUBORDINATE_SALES_TARGET_INVALID);
        }
    }
}
