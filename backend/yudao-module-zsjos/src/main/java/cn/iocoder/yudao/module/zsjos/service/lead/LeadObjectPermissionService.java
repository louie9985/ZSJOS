package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.HashSet;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class LeadObjectPermissionService {

    private static final String QUERY_ALL_PERMISSION = "zsjos:lead:query-all";
    private static final String QUALIFICATION_MANAGE_ALL_PERMISSION = "zsjos:lead:qualification:manage-all";

    @Resource
    private LeadMapper leadMapper;
    @Resource
    private SecurityFrameworkService securityFrameworkService;
    @Resource private AdminUserApi adminUserApi;
    @Resource private DeptApi deptApi;

    public void check(Long leadId, String action) {
        LeadDO lead = leadMapper.selectById(leadId);
        if (lead == null) {
            throw exception(LEAD_NOT_EXISTS);
        }
        Long userId = getLoginUserId();
        boolean allowed = switch (action) {
            case "read", "follow-up-read" -> canRead(lead, userId);
            case "pending-read", "accept", "reject" -> ASSIGNMENT_PENDING.equals(lead.getAssignmentStatus())
                    && Objects.equals(userId, lead.getPendingAssigneeUserId());
            case "owner-read" -> Objects.equals(userId, lead.getOwnerUserId());
            case "follow-up-create" -> Objects.equals(userId, lead.getOwnerUserId())
                    && (STATUS_INVALID.equals(lead.getStatus())
                    || STATUS_VALID.equals(lead.getStatus())
                    || "converted".equals(lead.getStatus())
                    || ASSIGNMENT_OWNED.equals(lead.getAssignmentStatus()) && STATUS_SUBMITTED.equals(lead.getStatus()));
            case "qualify" -> Objects.equals(userId, lead.getOwnerUserId())
                    && (ASSIGNMENT_OWNED.equals(lead.getAssignmentStatus())
                    || STATUS_VALID.equals(lead.getStatus()) || "converted".equals(lead.getStatus()));
            case "basic-info-update" -> Objects.equals(userId, lead.getOwnerUserId());
            case "claim" -> ASSIGNMENT_PUBLIC_POOL.equals(lead.getAssignmentStatus());
            case "admin-transfer" -> true; // Controller feature permission remains mandatory.
            case "qualification-manage" -> canManageQualificationException(lead, userId);
            default -> false;
        };
        if (!allowed) {
            throw exception(LEAD_PERMISSION_DENIED);
        }
    }

    public boolean canRead(LeadDO lead, Long userId) {
        return Objects.equals(userId, lead.getSourceUserId())
                || Objects.equals(userId, lead.getOwnerUserId())
                || securityFrameworkService.hasPermission(QUERY_ALL_PERMISSION)
                || managesOwnerDepartment(userId, lead.getOwnerUserId())
                || ASSIGNMENT_RECYCLE_PENDING.equals(lead.getAssignmentStatus())
                    && managesOwnerDepartment(userId, lead.getRecycleSourceOwnerUserId());
    }

    private boolean managesOwnerDepartment(Long userId, Long ownerUserId) {
        if (ownerUserId == null) return false;
        AdminUserRespDTO owner = adminUserApi.getUser(ownerUserId);
        if (owner == null || owner.getDeptId() == null) return false;
        for (DeptRespDTO managed : deptApi.getDeptListByLeaderUserId(userId)) {
            if (Objects.equals(managed.getId(), owner.getDeptId())
                    || deptApi.getChildDeptList(managed.getId()).stream()
                    .anyMatch(child -> Objects.equals(child.getId(), owner.getDeptId()))) {
                return true;
            }
        }
        return false;
    }

    public boolean canManageQualificationException(LeadDO lead, Long userId) {
        if (securityFrameworkService.hasPermission(QUALIFICATION_MANAGE_ALL_PERMISSION)) return true;
        Long scopedOwnerId = ASSIGNMENT_RECYCLE_PENDING.equals(lead.getAssignmentStatus())
                ? lead.getRecycleSourceOwnerUserId() : lead.getOwnerUserId();
        return managesOwnerDepartment(userId, scopedOwnerId);
    }

    public boolean hasQualificationManageAll() {
        return securityFrameworkService.hasPermission(QUALIFICATION_MANAGE_ALL_PERMISSION);
    }

    public Set<Long> getManagedUserIds(Long leaderUserId) {
        Set<Long> deptIds = new HashSet<>();
        for (DeptRespDTO managed : deptApi.getDeptListByLeaderUserId(leaderUserId)) {
            deptIds.add(managed.getId());
            deptApi.getChildDeptList(managed.getId()).forEach(child -> deptIds.add(child.getId()));
        }
        if (deptIds.isEmpty()) return Set.of();
        return new HashSet<>(adminUserApi.getUserListByDeptIds(deptIds).stream()
                .map(AdminUserRespDTO::getId).toList());
    }
}
