package cn.iocoder.yudao.module.zsjos.service.order;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderApprovalConfigDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderApprovalConfigMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class SalesOrderObjectPermissionService {
    @Resource private SalesOrderMapper orderMapper;
    @Resource private LeadMapper leadMapper;
    @Resource private SalesOrderApprovalConfigMapper configMapper;
    @Resource private DeptApi deptApi;
    @Resource private AdminUserApi adminUserApi;

    public void check(Long orderId, String action) {
        SalesOrderDO order = orderMapper.selectById(orderId);
        if (order == null) throw exception(SALES_ORDER_NOT_EXISTS);
        Long userId = getLoginUserId();
        boolean allowed = switch (action) {
            case "read" -> canRead(order, userId);
            case "revise" -> canRevise(order, userId);
            case "review" -> isApprovalPoolMember(userId);
            default -> false;
        };
        if (!allowed) throw exception(SALES_ORDER_PERMISSION_DENIED);
    }

    public boolean canRead(SalesOrderDO order, Long userId) {
        LeadDO lead = leadMapper.selectById(order.getLeadId());
        return Objects.equals(order.getSubmitterUserId(), userId)
                || lead != null && Objects.equals(lead.getOwnerUserId(), userId)
                || isApprovalPoolMember(userId);
    }

    public boolean canRevise(SalesOrderDO order, Long userId) {
        LeadDO lead = leadMapper.selectById(order.getLeadId());
        return lead != null && Objects.equals(lead.getOwnerUserId(), userId);
    }

    public boolean isApprovalPoolMember(Long userId) {
        AdminUserRespDTO user = adminUserApi.getUser(userId);
        if (user == null || !CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus()) || user.getDeptId() == null) return false;
        SalesOrderApprovalConfigDO config = configMapper.selectCurrent();
        return config != null && (belongsTo(config.getRegistrationDeptId(), user.getDeptId())
                || belongsTo(config.getFinanceDeptId(), user.getDeptId()));
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

    private boolean belongsTo(Long rootDeptId, Long userDeptId) {
        if (Objects.equals(rootDeptId, userDeptId)) return true;
        return rootDeptId != null && deptApi.getChildDeptList(rootDeptId).stream()
                .anyMatch(item -> Objects.equals(item.getId(), userDeptId));
    }
}
