package cn.iocoder.yudao.module.zsjos.service.deliveryclass;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.DeliveryClassDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.DeliveryClassMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@Slf4j
public class UnscheduledDeliveryClassService {
    @Resource private DeliveryClassMapper classes;
    @Resource private DeliveryClassService classService;
    @Resource private AdminUserApi users;
    @Resource private DeptApi depts;
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
        // Historical enrollment can reuse an existing container, but must not recreate a retired empty class.
        return null;
    }

    public void reconcile() {
        for (Long id : permissions.getEnabledUserIdsByPermission("zsjos:sales:eligible")) {
            var user = users.getUser(id);
            if (user == null || !Objects.equals(user.getStatus(), CommonStatusEnum.ENABLE.getStatus())) continue;
            try { ensure(id); }
            catch (RuntimeException failure) { log.error("[reconcile][unscheduled class failed for planner {}]", id, failure); }
        }
    }
}
