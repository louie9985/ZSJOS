package cn.iocoder.yudao.module.zsjos.service.personnel;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.system.api.notify.NotifyRecipientWecomUserProvider;
import cn.iocoder.yudao.module.system.api.social.SocialUserApi;
import cn.iocoder.yudao.module.system.api.social.dto.SocialUserRespDTO;
import cn.iocoder.yudao.module.system.enums.social.SocialTypeEnum;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerAccountDO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class PartnerNotifyRecipientWecomUserProvider implements NotifyRecipientWecomUserProvider {

    @Resource
    private PartnerAccountService partnerAccountService;
    @Resource
    private SocialUserApi socialUserApi;

    @Override
    public Integer getUserType() {
        return UserTypeEnum.PARTNER.getValue();
    }

    @Override
    public String getWecomUserId(Long userId) {
        try {
            partnerAccountService.requireContext(userId);
            PartnerAccountDO account = partnerAccountService.getById(userId);
            if (account == null || !Boolean.TRUE.equals(account.getWecomEnabled())) {
                return null;
            }
            SocialUserRespDTO wecom = socialUserApi.getSocialUserByUserId(UserTypeEnum.PARTNER.getValue(),
                    userId, SocialTypeEnum.WECHAT_ENTERPRISE.getType());
            return wecom == null ? null : StrUtil.trimToNull(wecom.getOpenid());
        } catch (ServiceException ignored) {
            return null;
        }
    }
}
