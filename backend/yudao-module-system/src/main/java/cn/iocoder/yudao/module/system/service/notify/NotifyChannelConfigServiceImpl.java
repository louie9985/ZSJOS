package cn.iocoder.yudao.module.system.service.notify;

import cn.iocoder.yudao.module.system.api.notify.dto.NotifyChannelConfig;
import cn.iocoder.yudao.module.system.dal.dataobject.notify.NotifyChannelConfigDO;
import cn.iocoder.yudao.module.system.dal.mysql.notify.NotifyChannelConfigMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.module.system.dal.dataobject.social.SocialClientDO;
import cn.iocoder.yudao.module.system.dal.mysql.social.SocialClientMapper;
import cn.iocoder.yudao.module.system.enums.social.SocialTypeEnum;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.NOTIFY_CHANNEL_CREDENTIAL_INVALID;
import cn.iocoder.yudao.module.system.api.notify.NotifyChannelType;

@Service
public class NotifyChannelConfigServiceImpl implements NotifyChannelConfigService {

    @Resource
    private NotifyChannelConfigMapper mapper;
    @jakarta.annotation.Resource
    private SocialClientMapper socialClientMapper;

    @Override
    public NotifyChannelConfig getEnabled(Long tenantId, String channelCode) {
        NotifyChannelConfigDO config = mapper.selectByChannelCode(channelCode);
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            return null;
        }
        return NotifyChannelConfig.builder()
                .tenantId(tenantId)
                .channelCode(config.getChannelCode())
                .provider(config.getConfigRef())
                .configJson(config.getMaskedConfig())
                .enabled(true)
                .build();
    }

    @Override
    public NotifyChannelConfig get(String channelCode) {
        NotifyChannelConfigDO config = mapper.selectByChannelCode(channelCode);
        if (config == null) return null;
        return NotifyChannelConfig.builder().tenantId(config.getTenantId()).channelCode(config.getChannelCode())
                .provider(config.getConfigRef()).configJson(config.getMaskedConfig()).enabled(config.getEnabled()).build();
    }

    @Override
    public void updateEnabled(String channelCode, boolean enabled) {
        NotifyChannelConfigDO config = mapper.selectByChannelCode(channelCode);
        if (config == null) {
            config = new NotifyChannelConfigDO();
            config.setChannelCode(channelCode);
            config.setEnabled(false);
            config.setConfigRef("system_social_client");
            config.setMaskedConfig("企业微信自建应用配置");
            mapper.insert(config);
        }
        if (enabled && NotifyChannelType.WECOM.equals(channelCode)) {
            SocialClientDO client = socialClientMapper.selectBySocialTypeAndUserType(
                    SocialTypeEnum.WECHAT_ENTERPRISE.getType(), UserTypeEnum.ADMIN.getValue());
            if (client == null || !CommonStatusEnum.ENABLE.getStatus().equals(client.getStatus())
                    || StrUtil.hasBlank(client.getClientId(), client.getClientSecret(), client.getAgentId())
                    || !StrUtil.startWith(client.getClientId(), "ww")
                    || !NumberUtil.isInteger(client.getAgentId())) {
                throw exception(NOTIFY_CHANNEL_CREDENTIAL_INVALID);
            }
        }
        mapper.updateEnabled(config.getId(), enabled);
    }
}
