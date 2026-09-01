package cn.iocoder.yudao.module.system.api.notify;

import cn.iocoder.yudao.module.system.api.notify.dto.NotifyTimingRuleRespDTO;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyDefaultRuleReqDTO;
import cn.iocoder.yudao.module.system.service.notify.NotifyRuleService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
public class NotifyRuleApiImpl implements NotifyRuleApi {
    @Resource private NotifyRuleService notifyRuleService;

    @Override
    public List<NotifyTimingRuleRespDTO> getEnabledTimingRules(Collection<String> sceneCodes) {
        if (sceneCodes == null || sceneCodes.isEmpty()) return List.of();
        return sceneCodes.stream().distinct().flatMap(sceneCode -> notifyRuleService.getEnabledRules(sceneCode).stream())
                .filter(rule -> rule.getTimingStage() != null && rule.getTimingOffsetMinutes() != null)
                .map(rule -> new NotifyTimingRuleRespDTO(rule.getId(), rule.getSceneCode(),
                        rule.getTimingStage(), rule.getTimingOffsetMinutes()))
                .toList();
    }

    @Override
    public void initializeDefaultRules(List<NotifyDefaultRuleReqDTO> rules) {
        notifyRuleService.initializeDefaultRules(rules);
    }
}
