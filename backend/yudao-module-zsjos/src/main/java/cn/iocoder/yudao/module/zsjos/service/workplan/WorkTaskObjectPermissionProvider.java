package cn.iocoder.yudao.module.zsjos.service.workplan;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.workplan.WorkTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.workplan.WorkTaskMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.WorkPlanConstants.BIZ_TYPE_WORK_TASK;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.WORK_PLAN_PERMISSION_DENIED;

@Component
public class WorkTaskObjectPermissionProvider implements ZsjosObjectPermissionProvider {
    @Resource private WorkTaskMapper taskMapper;
    @Resource private WorkPlanObjectPermissionProvider planPermissionProvider;

    @Override
    public String getBizType() {
        return BIZ_TYPE_WORK_TASK;
    }

    @Override
    public boolean hasPermission(Long taskId, String action, Long userId) {
        WorkTaskDO task = taskMapper.selectById(taskId);
        return task != null && planPermissionProvider.hasTaskPermission(task, action, userId);
    }

    @Override
    public void check(Long bizId, String action, Long userId) {
        if (!hasPermission(bizId, action, userId)) throw exception(WORK_PLAN_PERMISSION_DENIED);
    }
}
