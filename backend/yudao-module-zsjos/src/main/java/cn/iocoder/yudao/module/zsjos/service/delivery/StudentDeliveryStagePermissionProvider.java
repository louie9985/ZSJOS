package cn.iocoder.yudao.module.zsjos.service.delivery;

import cn.iocoder.yudao.module.zsjos.dal.mysql.delivery.StudentDeliveryStageMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.service.delivery.StudentDeliveryErrors.DEFER_PERMISSION_DENIED;

@Component
public class StudentDeliveryStagePermissionProvider implements ZsjosObjectPermissionProvider {
    public static final String BIZ_TYPE = "student-delivery-stage";
    @Resource private StudentDeliveryStageMapper stageMapper;
    @Resource private cn.iocoder.yudao.module.zsjos.service.account.MediaAccountObjectPermissionProvider accounts;

    @Override public String getBizType() { return BIZ_TYPE; }

    @Override public boolean hasPermission(Long id, String action, Long userId) {
        if (userId == null || !java.util.Set.of("defer", "submit").contains(action)) return false;
        var stage = stageMapper.selectById(id);
        return stage != null && userId.equals(stage.getDirectorUserId()) && accounts.hasPermission(stage.getAccountId(), "edit", userId);
    }

    @Override public void check(Long id, String action, Long userId) {
        if (!hasPermission(id, action, userId)) throw exception(DEFER_PERMISSION_DENIED);
    }
}
