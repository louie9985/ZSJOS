package cn.iocoder.yudao.module.zsjos.service.examcollection;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection.ExamCollectionMemberDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.DeliveryClassMapper;
import cn.iocoder.yudao.module.zsjos.service.deliveryclass.DeliveryClassObjectPermissionProvider;
import cn.iocoder.yudao.module.zsjos.service.deliveryclass.DeliveryClassService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class ExamCollectionRecipientScope {
    @Resource private AdminUserApi users;
    @Resource private PermissionApi permissions;
    @Resource private DeliveryClassMapper classes;
    @Resource private DeliveryClassObjectPermissionProvider classPermissions;

    public Set<Long> managedClassIds(Long userId) {
        if (!permissions.hasAnyPermissions(userId, DeliveryClassService.PERMISSION_QUERY_MANAGED)) return Set.of();
        Set<Long> result = new LinkedHashSet<>();
        for (var row : classes.selectList()) {
            if (classPermissions.hasPermission(row.getId(), "read", userId)) result.add(row.getId());
        }
        return result;
    }

    public Map<Long, List<ExamCollectionMemberDO>> managers(List<ExamCollectionMemberDO> roster) {
        Map<Long, List<ExamCollectionMemberDO>> result = new LinkedHashMap<>();
        if (roster.isEmpty()) return result;
        for (var user : users.getUserListByStatus(CommonStatusEnum.ENABLE.getStatus())) {
            if (!permissions.hasAnyPermissions(user.getId(), "zsjos:business-task:query")) continue;
            var visible = managedClassIds(user.getId());
            var own = roster.stream().filter(m -> visible.contains(m.getClassId())).toList();
            if (!own.isEmpty()) result.put(user.getId(), own);
        }
        return result;
    }

    public LambdaQueryWrapperX<ExamCollectionMemberDO> filter(Long taskId, Long userId) {
        var visible = managedClassIds(userId);
        var query = new LambdaQueryWrapperX<ExamCollectionMemberDO>()
                .eq(ExamCollectionMemberDO::getTaskId, taskId).eq(ExamCollectionMemberDO::getActive, true);
        query.and(q -> {
            q.eq(ExamCollectionMemberDO::getPlannerUserId, userId);
            if (!visible.isEmpty()) q.or().in(ExamCollectionMemberDO::getClassId, visible);
        });
        return query;
    }
}
