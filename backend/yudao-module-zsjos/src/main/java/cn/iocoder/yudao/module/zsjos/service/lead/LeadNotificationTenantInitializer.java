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
import static cn.iocoder.yudao.module.zsjos.enums.LeadNotifySceneConstants.ROLE_NEW_MEDIA_PROVIDER;
import static cn.iocoder.yudao.module.zsjos.enums.LeadNotifySceneConstants.ROLE_OPERATOR;

@Component
public class LeadNotificationTenantInitializer {

    private static final String CREATED_TEMPLATE_CODE = "ZSJOS_LEAD_CREATED";
    private static final String SOURCE_LINKED_TEMPLATE_CODE = "ZSJOS_LEAD_SOURCE_LINKED";

    @Resource
    private NotifyRuleApi notifyRuleApi;

    @EventListener
    public void onTenantCreated(TenantCreatedEvent event) {
        TenantUtils.execute(event.getTenantId(),
                () -> notifyRuleApi.initializeDefaultRules(defaultRules()));
    }

    static List<NotifyDefaultRuleReqDTO> defaultRules() {
        return List.of(
                NotifyDefaultRuleReqDTO.builder()
                        .name("销售客资提交成功")
                        .sceneCode(CREATED)
                        .templateCode(CREATED_TEMPLATE_CODE)
                        .recipientRoles(List.of(ROLE_OPERATOR))
                        .actionType(NotifyActionType.BUSINESS_DETAIL)
                        .build(),
                NotifyDefaultRuleReqDTO.builder()
                        .name("新媒体客资来源关联")
                        .sceneCode(CREATED)
                        .templateCode(SOURCE_LINKED_TEMPLATE_CODE)
                        .recipientRoles(List.of(ROLE_NEW_MEDIA_PROVIDER))
                        .actionType(NotifyActionType.BUSINESS_DETAIL)
                        .build());
    }
}
