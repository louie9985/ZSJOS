package cn.iocoder.yudao.module.system.service.notify;

import cn.iocoder.yudao.module.system.api.notify.dto.NotifyChannelConfig;
import cn.iocoder.yudao.module.system.dal.dataobject.notify.NotifyChannelConfigDO;
import cn.iocoder.yudao.module.system.dal.mysql.notify.NotifyChannelConfigMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class NotifyChannelConfigServiceImpl implements NotifyChannelConfigService {

    @Resource
    private NotifyChannelConfigMapper mapper;

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
                .build();
    }
}
