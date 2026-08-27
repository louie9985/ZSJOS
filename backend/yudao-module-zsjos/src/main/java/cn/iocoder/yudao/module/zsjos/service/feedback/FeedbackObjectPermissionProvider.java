package cn.iocoder.yudao.module.zsjos.service.feedback;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.feedback.FeedbackDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.feedback.FeedbackMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.FEEDBACK_PERMISSION_DENIED;
import static cn.iocoder.yudao.module.zsjos.service.feedback.FeedbackConstants.TYPE_PERMISSION;

@Component
public class FeedbackObjectPermissionProvider implements ZsjosObjectPermissionProvider {

    @Resource
    private FeedbackMapper feedbackMapper;
    @Resource
    private PermissionApi permissionApi;

    @Override
    public String getBizType() {
        return "feedback";
    }

    @Override
    public boolean hasPermission(Long feedbackId, String action, Long userId) {
        FeedbackDO feedback = feedbackMapper.selectById(feedbackId);
        if (feedback == null) return false;
        if (action.endsWith("-own")) {
            return Objects.equals(feedback.getSubmitterUserId(), userId);
        }
        if ("manage".equals(action) || "read-admin".equals(action)) {
            String permission = TYPE_PERMISSION.get(feedback.getFeedbackType());
            return permission != null && permissionApi.hasAnyPermissions(userId, permission);
        }
        return false;
    }

    @Override
    public void check(Long bizId, String action, Long userId) {
        if (!hasPermission(bizId, action, userId)) throw exception(FEEDBACK_PERMISSION_DENIED);
    }
}
