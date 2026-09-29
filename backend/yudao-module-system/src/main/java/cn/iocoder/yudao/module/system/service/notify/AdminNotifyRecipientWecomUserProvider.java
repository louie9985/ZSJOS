package cn.iocoder.yudao.module.system.service.notify;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.module.system.api.notify.NotifyRecipientWecomUserProvider;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class AdminNotifyRecipientWecomUserProvider implements NotifyRecipientWecomUserProvider {

    @Resource
    private AdminUserMapper userMapper;

    @Override
    public Integer getUserType() {
        return UserTypeEnum.ADMIN.getValue();
    }

    @Override
    public String getWecomUserId(Long userId) {
        AdminUserDO user = userMapper.selectById(userId);
        if (user == null || !CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus())
                || !Boolean.TRUE.equals(user.getWecomEnabled())) {
            return null;
        }
        return StrUtil.trimToNull(user.getWecomUserId());
    }

    @Override
    public String getUnavailableReason(Long userId) {
        AdminUserDO user = userMapper.selectById(userId);
        if (user == null || !CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus()))
            return "EMPLOYEE_UNAVAILABLE";
        if (!Boolean.TRUE.equals(user.getWecomEnabled())) return "WECOM_PERSONAL_PUSH_DISABLED";
        if (StrUtil.isBlank(user.getWecomUserId())) return "WECOM_BINDING_MISSING";
        // Eligibility may change between address lookup and reason lookup; never silently send a skipped attempt.
        return "WECOM_RECIPIENT_UNAVAILABLE";
    }
}
