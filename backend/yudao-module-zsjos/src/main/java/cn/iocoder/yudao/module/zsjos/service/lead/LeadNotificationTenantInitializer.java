package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.system.api.notify.NotifyActionType;
import cn.iocoder.yudao.module.system.api.notify.NotifyRuleApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyDefaultRuleReqDTO;
import cn.iocoder.yudao.module.system.api.tenant.dto.TenantCreatedEvent;
import jakarta.annotation.Resource;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

import static cn.iocoder.yudao.module.zsjos.enums.LeadNotifySceneConstants.CREATED;
import static cn.iocoder.yudao.module.zsjos.enums.LeadNotifySceneConstants.ROLE_OPERATOR;
import static cn.iocoder.yudao.module.zsjos.enums.LeadNotifySceneConstants.ROLE_SUBMITTER;

@Component
public class LeadNotificationTenantInitializer {

    private static final String CREATED_TEMPLATE_CODE = "ZSJOS_LEAD_CREATED";

    @Resource
    private NotifyRuleApi notifyRuleApi;

    @EventListener
    public void onTenantCreated(TenantCreatedEvent event) {
        TenantUtils.execute(event.getTenantId(),
                () -> notifyRuleApi.initializeDefaultRules(defaultRules()));
    }

    static List<NotifyDefaultRuleReqDTO> defaultRules() {
        return List.of(NotifyDefaultRuleReqDTO.builder()
                .name("客资新建通知")
                .sceneCode(CREATED)
                .templateCode(CREATED_TEMPLATE_CODE)
                .recipientRoles(List.of(ROLE_SUBMITTER, ROLE_OPERATOR))
                .actionType(NotifyActionType.BUSINESS_DETAIL)
                .build());
    }
}
