package cn.iocoder.yudao.module.zsjos.service.order;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.system.api.notify.NotifyActionType;
import cn.iocoder.yudao.module.system.api.notify.NotifyRuleApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyDefaultRuleReqDTO;
import cn.iocoder.yudao.module.system.api.tenant.dto.TenantCreatedEvent;
import jakarta.annotation.Resource;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

import static cn.iocoder.yudao.module.zsjos.enums.SalesOrderNotifySceneConstants.*;

@Component
public class SalesOrderNotificationTenantInitializer {

    @Resource private NotifyRuleApi notifyRuleApi;

    @EventListener
    public void onTenantCreated(TenantCreatedEvent event) {
        TenantUtils.execute(event.getTenantId(), () -> notifyRuleApi.initializeDefaultRules(defaultRules()));
    }

    static List<NotifyDefaultRuleReqDTO> defaultRules() {
        return List.of(
                NotifyDefaultRuleReqDTO.builder().name("成交订单主管确认申请")
                        .sceneCode(SUPERVISOR_REQUESTED).templateCode("ZSJOS_ORDER_SUPERVISOR_REQUESTED")
                        .recipientRoles(List.of(ROLE_SUPERVISOR)).actionType(NotifyActionType.BUSINESS_DETAIL).build(),
                NotifyDefaultRuleReqDTO.builder().name("成交订单主管确认结果")
                        .sceneCode(SUPERVISOR_DECIDED).templateCode("ZSJOS_ORDER_SUPERVISOR_DECIDED")
                        .recipientRoles(List.of(ROLE_REQUESTER)).actionType(NotifyActionType.BUSINESS_DETAIL).build());
    }
}
