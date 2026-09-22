package cn.iocoder.yudao.module.zsjos.service.payment;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.system.api.notify.NotifyActionType;
import cn.iocoder.yudao.module.system.api.notify.NotifyRuleApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyDefaultRuleReqDTO;
import cn.iocoder.yudao.module.system.api.tenant.dto.TenantCreatedEvent;
import jakarta.annotation.Resource;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class PaymentNotificationTenantInitializer {
    @Resource private NotifyRuleApi rules;

    @EventListener
    public void onTenantCreated(TenantCreatedEvent event) {
        TenantUtils.execute(event.getTenantId(), () -> rules.initializeDefaultRules(List.of(
                NotifyDefaultRuleReqDTO.builder().name("在线收款到账待补录")
                        .sceneCode(PaymentNotifySceneProvider.PAID).templateCode(PaymentNotifySceneProvider.TEMPLATE)
                        .recipientRoles(List.of(PaymentNotifySceneProvider.OWNER))
                        .actionType(NotifyActionType.MESSAGE_DETAIL).build())));
    }
}
