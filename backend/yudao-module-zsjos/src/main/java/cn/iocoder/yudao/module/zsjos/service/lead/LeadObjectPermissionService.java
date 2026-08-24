package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAgingPoolCycleMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadPublicSeaRecordMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.service.order.SalesOrderObjectPermissionService;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAgingPoolCycleDO;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
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
    @Resource private LeadAgingPoolCycleMapper agingPoolCycleMapper;
    @Resource private ServiceRelationMapper serviceRelationMapper;
    @Resource private LeadPublicSeaRecordMapper publicSeaRecordMapper;
    @Resource private SalesOrderMapper salesOrderMapper;
    @Resource private SalesOrderObjectPermissionService salesOrderObjectPermissionService;
    @Resource private LeadAgingPoolService leadAgingPoolService;
    @Resource private MediaAccountMapper mediaAccountMapper;

    public void check(Long leadId, String action) {
        LeadDO lead = leadMapper.selectById(leadId);
        if (lead == null) {
            throw exception(LEAD_NOT_EXISTS);
        }
        Long userId = getLoginUserId();
        boolean allowed = switch (action) {
            case "read", "follow-up-read", "flow-read" -> canReadDetail(lead, userId);
            case "pending-read", "accept", "reject" -> ASSIGNMENT_PENDING.equals(lead.getAssignmentStatus())
                    && Objects.equals(userId, lead.getPendingAssigneeUserId());
            case "owner-read" -> Objects.equals(userId, lead.getOwnerUserId());
            case "owner-or-manager-read" -> canReadAsOwnerOrManager(lead, userId);
            case "sales-history-read" -> canReadDetail(lead, userId);
            case "follow-up-create" -> canOperateAsSales(lead, userId)
                    && (STATUS_INVALID.equals(lead.getStatus())
                    || STATUS_VALID.equals(lead.getStatus())
                    || ASSIGNMENT_OWNED.equals(lead.getAssignmentStatus()) && STATUS_SUBMITTED.equals(lead.getStatus()));
            case "qualify" -> agingPoolCycleMapper.selectActiveByLeadId(lead.getId()) == null
                    && Objects.equals(userId, lead.getOwnerUserId())
                    && (ASSIGNMENT_OWNED.equals(lead.getAssignmentStatus())
                    || STATUS_VALID.equals(lead.getStatus()));
            case "enter-deal" -> canOperateAsSales(lead, userId);
            case "basic-info-update" -> agingPoolCycleMapper.selectActiveByLeadId(lead.getId()) == null
                    && Objects.equals(userId, lead.getOwnerUserId());
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
        return securityFrameworkService.hasPermission(QUERY_ALL_PERMISSION)
                || securityFrameworkService.hasPermission(PERMISSION_QUERY_SUBMITTED)
                    && (Objects.equals(userId, lead.getSourceUserId())
                    || managesUserDepartment(userId, lead.getSourceUserId()))
                || securityFrameworkService.hasPermission(PERMISSION_QUERY_OWNED)
                    && (Objects.equals(userId, lead.getOwnerUserId())
                    || managesUserDepartment(userId, lead.getOwnerUserId()))
                || ASSIGNMENT_RECYCLE_PENDING.equals(lead.getAssignmentStatus())
                    && managesUserDepartment(userId, lead.getRecycleSourceOwnerUserId());
    }

    public boolean hasQueryAll() {
        return securityFrameworkService.hasPermission(QUERY_ALL_PERMISSION);
    }

    /**
     * Unified Lead detail visibility. List scope remains owned by the individual business inboxes.
     */
    public boolean canReadDetail(LeadDO lead, Long userId) {
        if (userId == null) return false;
        if (hasQueryAll() || Objects.equals(userId, lead.getSourceUserId())
                || Objects.equals(userId, lead.getOwnerUserId())
                || managesUserDepartment(userId, lead.getOwnerUserId())
                || canReadAgingPool(lead.getId(), userId)
                || canReadManualPublicSea(lead, userId) || canReadStudentSalesHistory(lead, userId)
                || canReadMediaStudentLead(lead, userId)) {
            return true;
        }
        return salesOrderMapper.selectByLeadId(lead.getId()).stream()
                .anyMatch(order -> salesOrderObjectPermissionService.canRead(order, userId));
    }

    public boolean canReadStudentSalesHistory(LeadDO lead, Long userId) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        return serviceRelationMapper.countActiveByOwnerAndLead(userId, lead.getId(), tenantId) > 0
                || serviceRelationMapper.countActiveByParticipantAndLead(userId, lead.getId(), tenantId) > 0;
    }

    public boolean canReadMediaStudentLead(LeadDO lead, Long userId) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        return mediaAccountMapper.countParticipantByLead(userId, lead.getId(), tenantId) > 0;
    }

    public boolean canReadAsOwnerOrManager(LeadDO lead, Long userId) {
        return Objects.equals(userId, lead.getOwnerUserId())
                || hasQueryAll()
                || managesUserDepartment(userId, lead.getOwnerUserId());
    }

    /**
     * 管理员及负责人部门主管可以查看提交人与负责人的完整员工身份信息。
     */
    public boolean canViewUnmaskedIdentity(Long userId, Long ownerUserId) {
        return hasQueryAll() || managesUserDepartment(userId, ownerUserId);
    }

    public boolean canViewUnmaskedIdentity(Long userId, LeadDO lead) {
        return canViewUnmaskedIdentity(userId, lead.getOwnerUserId());
    }

    private boolean canOperateAsSales(LeadDO lead, Long userId) {
        LeadAgingPoolCycleDO cycle = agingPoolCycleMapper.selectActiveByLeadId(lead.getId());
        if (cycle != null) {
            return Set.of(AGING_POOL_ASSIGNED, AGING_POOL_DEAL_PENDING).contains(cycle.getStatus())
                    && (Objects.equals(userId, lead.getOwnerUserId())
                    || Objects.equals(userId, cycle.getCollaboratorUserId()));
        }
        var manual = publicSeaRecordMapper.selectByLeadId(lead.getId());
        return Objects.equals(userId, lead.getOwnerUserId()) || manual != null
                && Objects.equals(userId, manual.getCollaboratorUserId());
    }

    private boolean canReadAgingPool(Long leadId, Long userId) {
        return leadAgingPoolService.canRead(leadId, userId);
    }

    private boolean canReadManualPublicSea(LeadDO lead, Long userId) {
        var record = publicSeaRecordMapper.selectByLeadId(lead.getId());
        return record != null && (Objects.equals(userId, record.getOwnerUserId())
                || Objects.equals(userId, record.getCollaboratorUserId())
                || managesUserDepartment(userId, record.getOwnerUserId()));
    }

    private boolean managesUserDepartment(Long userId, Long relatedUserId) {
        if (relatedUserId == null) return false;
        AdminUserRespDTO relatedUser = adminUserApi.getUser(relatedUserId);
        if (relatedUser == null || relatedUser.getDeptId() == null) return false;
        for (DeptRespDTO managed : deptApi.getDeptListByLeaderUserId(userId)) {
            if (Objects.equals(managed.getId(), relatedUser.getDeptId())
                    || deptApi.getChildDeptList(managed.getId()).stream()
                    .anyMatch(child -> Objects.equals(child.getId(), relatedUser.getDeptId()))) {
                return true;
            }
        }
        return false;
    }

    public boolean canManageQualificationException(LeadDO lead, Long userId) {
        if (securityFrameworkService.hasPermission(QUALIFICATION_MANAGE_ALL_PERMISSION)) return true;
        Long scopedOwnerId = ASSIGNMENT_RECYCLE_PENDING.equals(lead.getAssignmentStatus())
                ? lead.getRecycleSourceOwnerUserId() : lead.getOwnerUserId();
        return managesUserDepartment(userId, scopedOwnerId);
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

    public Set<Long> getRelatedAndManagedUserIds(Long userId) {
        Set<Long> userIds = new HashSet<>(getManagedUserIds(userId));
        userIds.add(userId);
        return userIds;
    }
}
