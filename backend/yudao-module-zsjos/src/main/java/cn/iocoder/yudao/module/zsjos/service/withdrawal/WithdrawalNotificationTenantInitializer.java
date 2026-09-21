package cn.iocoder.yudao.module.zsjos.service.withdrawal;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.system.api.notify.NotifyActionType;
import cn.iocoder.yudao.module.system.api.notify.NotifyRuleApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyDefaultRuleReqDTO;
import cn.iocoder.yudao.module.system.api.tenant.dto.TenantCreatedEvent;
import jakarta.annotation.Resource;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import java.util.List;
import static cn.iocoder.yudao.module.zsjos.enums.WithdrawalConstants.*;

@Component
public class WithdrawalNotificationTenantInitializer {
    @Resource private NotifyRuleApi notifyRuleApi;

    @EventListener
    public void onTenantCreated(TenantCreatedEvent event) {
        TenantUtils.execute(event.getTenantId(), () -> notifyRuleApi.initializeDefaultRules(defaultRules()));
    }

    static List<NotifyDefaultRuleReqDTO> defaultRules() {
        return List.of(
                NotifyDefaultRuleReqDTO.builder().name("提现申请待审核").sceneCode(SCENE_SUBMITTED)
                        .templateCode("ZSJ_N269_WITHDRAWAL_SUBMITTED")
                        .recipientRoles(List.of(ROLE_FINANCE))
                        .actionType(NotifyActionType.BUSINESS_DETAIL).build(),
                NotifyDefaultRuleReqDTO.builder().name("提现审批通过待打款").sceneCode(SCENE_APPROVED)
                        .templateCode("ZSJ_N269_WITHDRAWAL_APPROVED")
                        .recipientRoles(List.of(ROLE_APPLICANT, ROLE_FINANCE))
                        .actionType(NotifyActionType.BUSINESS_DETAIL).build(),
                NotifyDefaultRuleReqDTO.builder().name("提现申请已驳回").sceneCode(SCENE_REJECTED)
                        .templateCode("ZSJ_N269_WITHDRAWAL_REJECTED")
                        .recipientRoles(List.of(ROLE_APPLICANT, ROLE_FINANCE))
                        .actionType(NotifyActionType.BUSINESS_DETAIL).build(),
                NotifyDefaultRuleReqDTO.builder().name("提现已记录打款").sceneCode(SCENE_PAID)
                        .templateCode("ZSJ_N269_WITHDRAWAL_PAID")
                        .recipientRoles(List.of(ROLE_APPLICANT, ROLE_FINANCE))
                        .actionType(NotifyActionType.BUSINESS_DETAIL).build(),
                NotifyDefaultRuleReqDTO.builder().name("财务提现周期提醒").sceneCode(SCENE_FINANCE_REMINDER)
                        .templateCode("ZSJ_N269_WITHDRAWAL_FINANCE_REMINDER")
                        .recipientRoles(List.of(ROLE_FINANCE))
                        .actionType(NotifyActionType.NONE).build());
    }
}
