package cn.iocoder.yudao.module.zsjos.service.studentcontact;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.STUDENT_PERMISSION_DENIED;
import static cn.iocoder.yudao.module.zsjos.service.studentcontact.StudentContactConstants.TYPE_ASSISTANCE;

@Component
public class StudentAssistanceObjectPermissionProvider implements ZsjosObjectPermissionProvider {
    @Resource private BusinessTaskMapper taskMapper;

    @Override public String getBizType() { return "student-assistance"; }

    @Override public boolean hasPermission(Long bizId, String action, Long userId) {
        BusinessTaskDO task = taskMapper.selectById(bizId);
        return task != null && TYPE_ASSISTANCE.equals(task.getTaskType())
                && "complete".equals(action) && Objects.equals(task.getAssigneeId(), userId);
    }

    @Override public void check(Long bizId, String action, Long userId) {
        if (!hasPermission(bizId, action, userId)) throw exception(STUDENT_PERMISSION_DENIED);
    }
}
