package cn.iocoder.yudao.module.zsjos.service.deliveryclass;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.permission.RoleApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.DeliveryClassDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.DeliveryClassMapper;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import java.util.Set;
import java.util.Objects;

@Service
@lombok.extern.slf4j.Slf4j
public class ExceptionDeliveryClassService {
    @Resource private DeliveryClassMapper classes;
    @Resource private DeliveryClassService classService;
    @Resource private AdminUserApi users;
    @Resource private DeptApi depts;
    @Resource private RoleApi roles;
    @Resource private PermissionApi permissions;

    public DeliveryClassDO ensure(Long owner) {
        var user = classService.validateHomeroom(owner);
        var query = new LambdaQueryWrapperX<DeliveryClassDO>().eq(DeliveryClassDO::getClassKind,DeliveryClassKind.EXCEPTION)
            .eq(DeliveryClassDO::getHomeroomUserId,owner);
        var existing = classes.selectOne(query);
        var dept = user.getDeptId() == null ? null : depts.getDept(user.getDeptId());
        if (existing != null) {
            // This system class follows the authoritative planner, including department moves.
            if (!Objects.equals(existing.getDeptId(),user.getDeptId())
                || !Objects.equals(existing.getHomeroomUserNameSnapshot(),user.getNickname())
                || !Objects.equals(existing.getDeptNameSnapshot(),dept == null ? null : dept.getName())) {
                existing.setDeptId(user.getDeptId()).setHomeroomUserNameSnapshot(user.getNickname())
                    .setDeptNameSnapshot(dept == null ? null : dept.getName());
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
        var row = new DeliveryClassDO().setClassNo("EX"+owner).setClassName("异常结业")
            .setClassKind(DeliveryClassKind.EXCEPTION).setSystemClass(true).setHomeroomUserId(owner)
            .setHomeroomUserNameSnapshot(user.getNickname()).setDeptId(user.getDeptId())
            .setDeptNameSnapshot(dept == null ? null : dept.getName()).setStatus("SERVING").setVersion(0);
        try { classes.insert(row); return row; }
        catch (DuplicateKeyException conflict) {
            existing = classes.selectPlannerDefaultForUpdate(DeliveryClassKind.EXCEPTION, owner,
                    cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId());
            if (existing == null) throw conflict;
            return existing;
        }
    }

    public void reconcile() {
        // Same authoritative qualification as DeliveryClassService.validateHomeroom.
        var role = roles.getRoleByCode("study_planner");
        if (role == null || !Objects.equals(role.getStatus(),CommonStatusEnum.ENABLE.getStatus())) return;
        for (Long id : permissions.getUserRoleIdListByRoleIds(Set.of(role.getId()))) {
            try {
                var user = users.getUser(id);
                if (user != null && Objects.equals(user.getStatus(),CommonStatusEnum.ENABLE.getStatus())) ensure(id);
            } catch (RuntimeException failure) {
                log.error("[reconcile][exception class failed for planner {}]", id, failure);
            }
        }
    }
}
