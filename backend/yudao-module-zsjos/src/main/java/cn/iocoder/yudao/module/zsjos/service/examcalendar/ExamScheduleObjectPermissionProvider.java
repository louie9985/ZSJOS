package cn.iocoder.yudao.module.zsjos.service.examcalendar;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcalendar.ExamScheduleMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.EXAM_SCHEDULE_PERMISSION_DENIED;

@Component
public class ExamScheduleObjectPermissionProvider implements ZsjosObjectPermissionProvider {
    public static final String BIZ_TYPE = "exam-schedule";

    @Resource private ExamScheduleMapper mapper;
    @Resource private PermissionApi permissionApi;

    @Override
    public String getBizType() {
        return BIZ_TYPE;
    }

    @Override
    public boolean hasPermission(Long id, String action, Long userId) {
        return mapper.selectById(id) != null
                && permissionApi.hasAnyPermissions(userId, ExamScheduleService.PERMISSION_MANAGE)
                && ("update".equals(action) || "publish".equals(action) || "revoke".equals(action));
    }

    @Override
    public void check(Long id, String action, Long userId) {
        if (!hasPermission(id, action, userId)) throw exception(EXAM_SCHEDULE_PERMISSION_DENIED);
    }
}
