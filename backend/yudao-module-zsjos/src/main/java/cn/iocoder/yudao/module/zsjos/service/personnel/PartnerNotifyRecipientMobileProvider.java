package cn.iocoder.yudao.module.zsjos.service.personnel;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.system.api.notify.NotifyRecipientMobileProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class PartnerNotifyRecipientMobileProvider implements NotifyRecipientMobileProvider {

    @Resource
    private PartnerAccountService partnerAccountService;

    @Override
    public Integer getUserType() {
        return UserTypeEnum.PARTNER.getValue();
    }

    @Override
    public String getMobile(Long userId) {
        try {
            return partnerAccountService.getEnabledMobile(userId);
        } catch (ServiceException ignored) {
            return null;
        }
    }
}
