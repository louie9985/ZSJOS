package cn.iocoder.yudao.module.system.api.notify;

import cn.iocoder.yudao.module.system.api.notify.dto.NotifyTimingRuleRespDTO;

import java.util.Collection;
import java.util.List;

public interface NotifyRuleApi {
    List<NotifyTimingRuleRespDTO> getEnabledTimingRules(Collection<String> sceneCodes);
}
