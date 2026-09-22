package cn.iocoder.yudao.module.zsjos.service.payment;

import cn.iocoder.yudao.module.system.api.notify.NotifyActionType;
import cn.iocoder.yudao.module.system.api.notify.NotifySceneProvider;
import cn.iocoder.yudao.module.system.api.notify.dto.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class PaymentNotifySceneProvider implements NotifySceneProvider {
    public static final String PAID = "zsjos.payment.paid";
    public static final String OWNER = "owner";
    public static final String TEMPLATE = "ZSJOS_PAYMENT_PAID";

    @Override
    public List<NotifySceneRespDTO> getScenes() {
        return List.of(new NotifySceneRespDTO(PAID, "在线收款到账待补录", List.of(
                new NotifySceneVariableRespDTO("purchase.no", "购买意向编号", false),
                new NotifySceneVariableRespDTO("payment.no", "支付单号", false),
                new NotifySceneVariableRespDTO("payment.amount", "到账金额", false)),
                List.of(new NotifySceneRoleRespDTO(OWNER, "到账时购买意向负责人")),
                List.of(NotifyActionType.MESSAGE_DETAIL, NotifyActionType.NONE), false));
    }

    @Override
    public Set<NotifyRecipientDTO> resolveRecipients(NotifyBusinessEvent event, Set<String> roles) {
        Object owner = payload(event).get("ownerUserId");
        return roles.contains(OWNER) && owner instanceof Number id && id.longValue() > 0
                ? Set.of(NotifyRecipientDTO.admin(id.longValue())) : Set.of();
    }

    @Override
    public Map<String, Object> resolveVariables(NotifyBusinessEvent event, NotifyRecipientDTO recipient) {
        return payload(event);
    }

    private Map<String, Object> payload(NotifyBusinessEvent event) {
        return event.getPayload() == null ? Map.of() : event.getPayload();
    }
}
