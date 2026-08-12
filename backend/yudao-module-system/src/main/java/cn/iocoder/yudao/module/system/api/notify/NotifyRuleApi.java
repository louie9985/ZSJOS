package cn.iocoder.yudao.module.system.api.notify;

import cn.iocoder.yudao.module.system.api.notify.dto.NotifyTimingRuleRespDTO;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyDefaultRuleReqDTO;

import java.util.Collection;
import java.util.List;

public interface NotifyRuleApi {
    List<NotifyTimingRuleRespDTO> getEnabledTimingRules(Collection<String> sceneCodes);
    void initializeDefaultRules(List<NotifyDefaultRuleReqDTO> rules);
}
