package cn.iocoder.yudao.module.zsjos.service.order;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderApprovalConfigDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderApprovalConfigMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderSupervisorConfirmationMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.*;

@Service
public class SalesOrderObjectPermissionService {
    @Resource private SalesOrderMapper orderMapper;
    @Resource private LeadMapper leadMapper;
    @Resource private SalesOrderApprovalConfigMapper salesOrderApprovalConfigMapper;
    @Resource private SalesOrderSupervisorConfirmationMapper supervisorConfirmationMapper;
    @Resource private DeptApi deptApi;
    @Resource private AdminUserApi adminUserApi;
    @Resource private PermissionApi permissionApi;
    @Resource private cn.iocoder.yudao.module.zsjos.service.lead.LeadAgingPoolService agingPoolService;

    public void check(Long orderId, String action) {
        SalesOrderDO order = orderMapper.selectById(orderId);
        if (order == null) throw exception(SALES_ORDER_NOT_EXISTS);
        Long userId = getLoginUserId();
        boolean allowed = switch (action) {
            case "read" -> permissionApi.hasTenantReadAllAccess(userId) || canRead(order, userId);
            case "read-management" -> canReadManagement(order, userId);
            case "read-own" -> permissionApi.hasTenantReadAllAccess(userId) || Objects.equals(order.getSubmitterUserId(), userId);
            case "revise" -> canRevise(order, userId);
            case "continue-revise" -> canContinue(order, userId);
            case "terminate" -> Objects.equals(order.getSubmitterUserId(), userId)
                    || Objects.equals(order.getFormalSalesUserId(), userId);
            case "review" -> isApprovalPoolMember(userId);
            default -> false;
        };
        if (!allowed) throw exception(SALES_ORDER_PERMISSION_DENIED);
    }

    public boolean canRead(SalesOrderDO order, Long userId) {
        LeadDO lead = order.getLeadId() == null ? null : leadMapper.selectById(order.getLeadId());
        return Objects.equals(order.getSubmitterUserId(), userId)
                || Objects.equals(order.getFormalSalesUserId(), userId)
                || lead != null && Objects.equals(lead.getOwnerUserId(), userId)
                || order.getLeadId() != null && agingPoolService.canRead(order.getLeadId(), userId)
                || isApprovalPoolMember(userId) || isCurrentSupervisor(order, userId)
                || isTeamOrderReader(order, userId);
    }

    public boolean canRevise(SalesOrderDO order, Long userId) {
        return (Objects.equals(order.getSubmitterUserId(), userId) || Objects.equals(order.getFormalSalesUserId(), userId))
                && order.getSupersededByOrderId() == null;
    }

    public boolean canContinue(SalesOrderDO order, Long userId) {
        if (order.getLeadId() == null) return false;
        var cycle = agingPoolService.getActiveCycle(order.getLeadId());
        return cycle != null && Objects.equals(cycle.getCollaboratorUserId(), userId)
                && !Objects.equals(order.getSubmitterUserId(), userId) && order.getSupersededByOrderId() == null;
    }

    public boolean isApprovalPoolMember(Long userId) {
        return !approvalTaskKeys(userId).isEmpty();
    }

    public Set<String> approvalTaskKeys(Long userId) {
        AdminUserRespDTO user = adminUserApi.getUser(userId);
        if (user == null || !CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus()) || user.getDeptId() == null) return Set.of();
        SalesOrderApprovalConfigDO config = salesOrderApprovalConfigMapper.selectCurrent();
        if (config == null) return Set.of();
        Set<String> result = new LinkedHashSet<>();
        if (belongsTo(config.getRegistrationDeptId(), user.getDeptId())) result.add(TASK_REGISTRATION);
        if (belongsTo(config.getFinanceDeptId(), user.getDeptId())) result.add(TASK_FINANCE);
        return result;
    }

    public boolean isFinanceCenterMember(Long userId) {
        return approvalTaskKeys(userId).contains(TASK_FINANCE);
    }

    public Set<Long> enabledUsers(Long rootDeptId) {
        if (rootDeptId == null) return Set.of();
        Set<Long> deptIds = new LinkedHashSet<>();
        deptIds.add(rootDeptId);
        deptApi.getChildDeptList(rootDeptId).forEach(item -> deptIds.add(item.getId()));
        Set<Long> users = new LinkedHashSet<>();
        adminUserApi.getUserListByDeptIds(deptIds).stream()
                .filter(user -> CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus()))
                .map(AdminUserRespDTO::getId).sorted().forEach(users::add);
        return users;
    }

    public Set<Long> teamUserIds(Long userId) {
        AdminUserRespDTO user = adminUserApi.getUser(userId);
        if (user == null || user.getDeptId() == null
                || permissionApi == null || !permissionApi.hasAnyPermissions(userId, PERMISSION_QUERY_TEAM)) return Set.of();
        return enabledUsers(user.getDeptId());
    }

    /**
     * Resolves the submitters visible to the unified order-management list from
     * System data scope. The result is tenant-scoped by the System APIs.
     */
    public SalesOrderManagementScope resolveManagementScope(Long userId) {
        if (permissionApi == null) return SalesOrderManagementScope.empty();
        if (permissionApi.hasTenantReadAllAccess(userId)) {
            return new SalesOrderManagementScope(true, true, Set.of(), Set.of());
        }
        DeptDataPermissionRespDTO scope = permissionApi.getDeptDataPermission(userId);
        if (scope == null) return SalesOrderManagementScope.empty();
        Set<Long> result = new LinkedHashSet<>();
        if (Boolean.TRUE.equals(scope.getSelf())) result.add(userId);
        if (Boolean.TRUE.equals(scope.getAll())) {
            return new SalesOrderManagementScope(true, true, scope.getDeptIds(), result);
        }
        if (scope.getDeptIds() != null && !scope.getDeptIds().isEmpty()) {
            adminUserApi.getUserListByDeptIds(scope.getDeptIds()).stream()
                    .filter(user -> CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus()))
                    .map(AdminUserRespDTO::getId).forEach(result::add);
        }
        return new SalesOrderManagementScope(false, Boolean.TRUE.equals(scope.getSelf()), scope.getDeptIds(), result);
    }

    public boolean canReadManagement(SalesOrderDO order, Long userId) {
        SalesOrderManagementScope scope = resolveManagementScope(userId);
        if (scope.isAll()) return true;
        if (scope.isEmpty()) return false;
        if (scope.getSubmitterUserIds().contains(order.getSubmitterUserId())) return true;
        if (scope.getDeptIds().isEmpty() || order.getSubmitterUserId() == null) return false;
        AdminUserRespDTO submitter = adminUserApi.getUser(order.getSubmitterUserId());
        return submitter != null
                && CommonStatusEnum.ENABLE.getStatus().equals(submitter.getStatus())
                && submitter.getDeptId() != null
                && scope.getDeptIds().contains(submitter.getDeptId());
    }

    private boolean isTeamOrderReader(SalesOrderDO order, Long userId) {
        return teamUserIds(userId).contains(order.getSubmitterUserId());
    }

    private boolean isCurrentSupervisor(SalesOrderDO order, Long userId) {
        if (order.getCurrentApprovalRoundId() == null) return false;
        return supervisorConfirmationMapper.selectByRoundId(order.getCurrentApprovalRoundId()).stream()
                .anyMatch(item -> Objects.equals(item.getSupervisorUserId(), userId));
    }

    private boolean belongsTo(Long rootDeptId, Long userDeptId) {
        if (Objects.equals(rootDeptId, userDeptId)) return true;
        return rootDeptId != null && deptApi.getChildDeptList(rootDeptId).stream()
                .anyMatch(item -> Objects.equals(item.getId(), userDeptId));
    }
}
