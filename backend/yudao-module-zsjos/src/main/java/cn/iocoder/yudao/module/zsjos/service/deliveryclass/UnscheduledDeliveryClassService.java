package cn.iocoder.yudao.module.zsjos.service.deliveryclass;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.permission.RoleApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.DeliveryClassDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.DeliveryClassMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Set;

@Service
@Slf4j
public class UnscheduledDeliveryClassService {
    @Resource private DeliveryClassMapper classes;
    @Resource private DeliveryClassService classService;
    @Resource private AdminUserApi users;
    @Resource private DeptApi depts;
    @Resource private RoleApi roles;
    @Resource private PermissionApi permissions;

    public DeliveryClassDO ensure(Long owner) {
        var user = classService.validateHomeroom(owner);
        var query = new LambdaQueryWrapperX<DeliveryClassDO>()
                .eq(DeliveryClassDO::getClassKind, DeliveryClassKind.UNSCHEDULED)
                .eq(DeliveryClassDO::getHomeroomUserId, owner);
        var existing = classes.selectOne(query);
        var dept = user.getDeptId() == null ? null : depts.getDept(user.getDeptId());
        String deptName = dept == null ? null : dept.getName();
        if (existing != null) {
            // This is a permanent owned container; organization changes follow System.
            if (!Objects.equals(existing.getDeptId(), user.getDeptId())
                    || !Objects.equals(existing.getHomeroomUserNameSnapshot(), user.getNickname())
                    || !Objects.equals(existing.getDeptNameSnapshot(), deptName)) {
                existing.setDeptId(user.getDeptId()).setDeptNameSnapshot(deptName)
                        .setHomeroomUserNameSnapshot(user.getNickname()).setVersion(existing.getVersion() + 1);
                if (classes.update(null, com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaUpdate(DeliveryClassDO.class)
                        .eq(DeliveryClassDO::getId, existing.getId())
                        .set(DeliveryClassDO::getDeptId, existing.getDeptId())
                        .set(DeliveryClassDO::getDeptNameSnapshot, existing.getDeptNameSnapshot())
                        .set(DeliveryClassDO::getHomeroomUserNameSnapshot, existing.getHomeroomUserNameSnapshot())
                        .setSql("version=version+1")) != 1)
                    throw cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception(
                        cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.DELIVERY_CLASS_VERSION_CONFLICT);
            }
            return existing;
        }
        var row = new DeliveryClassDO().setClassNo("US" + owner).setClassName("未确定考期")
                .setClassKind(DeliveryClassKind.UNSCHEDULED).setSystemClass(true)
                .setHomeroomUserId(owner).setHomeroomUserNameSnapshot(user.getNickname())
                .setDeptId(user.getDeptId()).setDeptNameSnapshot(deptName).setStatus("SERVING").setVersion(0);
        try { classes.insert(row); return row; }
        catch (DuplicateKeyException conflict) {
            // Current read also sees the winner inside a repeatable-read enrollment transaction.
            existing = classes.selectPlannerDefaultForUpdate(DeliveryClassKind.UNSCHEDULED, owner,
                    TenantContextHolder.getRequiredTenantId());
            if (existing == null) throw conflict;
            return existing;
        }
    }

    public void reconcile() {
        var role = roles.getRoleByCode("study_planner");
        if (role == null || !Objects.equals(role.getStatus(), CommonStatusEnum.ENABLE.getStatus())) return;
        for (Long id : permissions.getUserRoleIdListByRoleIds(Set.of(role.getId()))) {
            var user = users.getUser(id);
            if (user == null || !Objects.equals(user.getStatus(), CommonStatusEnum.ENABLE.getStatus())) continue;
            try { ensure(id); }
            catch (RuntimeException failure) { log.error("[reconcile][unscheduled class failed for planner {}]", id, failure); }
        }
    }
}
