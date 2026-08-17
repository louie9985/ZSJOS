package cn.iocoder.yudao.module.zsjos.service.withdrawal;

import cn.iocoder.yudao.module.system.api.notify.NotifyActionType;
import cn.iocoder.yudao.module.system.api.notify.NotifySceneProvider;
import cn.iocoder.yudao.module.system.api.notify.dto.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.personnel.PartnerAccountMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import java.util.*;
import static cn.iocoder.yudao.module.zsjos.enums.WithdrawalConstants.*;

@Component
public class WithdrawalNotifySceneProvider implements NotifySceneProvider {
    @Resource private PartnerAccountMapper partnerAccountMapper;
    @Override public List<NotifySceneRespDTO> getScenes() {
        return List.of(scene(SCENE_SUBMITTED, "提现申请待审核", ROLE_FINANCE),
                scene(SCENE_APPROVED, "提现审批通过待打款", ROLE_APPLICANT, ROLE_FINANCE),
                scene(SCENE_REJECTED, "提现申请已驳回", ROLE_APPLICANT, ROLE_FINANCE),
                scene(SCENE_PAID, "提现已记录打款", ROLE_APPLICANT, ROLE_FINANCE),
                scene(SCENE_FINANCE_REMINDER, "财务提现周期提醒", ROLE_FINANCE));
    }
    @Override public Set<NotifyRecipientDTO> resolveRecipients(NotifyBusinessEvent event, Set<String> roles) {
        Set<NotifyRecipientDTO> result = new LinkedHashSet<>(); Map<String, Object> payload = event.getPayload();
        if (roles.contains(ROLE_APPLICANT)) {
            if (payload.get("partnerId") instanceof Number partnerId) {
                PartnerAccountDO account = partnerAccountMapper.selectByPartnerId(partnerId.longValue());
                if (account != null) result.add(NotifyRecipientDTO.partner(account.getId()));
            } else if (payload.get("applicantUserId") instanceof Number n) result.add(NotifyRecipientDTO.admin(n.longValue()));
        }
        if (roles.contains(ROLE_FINANCE) && payload.get("financeUserIds") instanceof Collection<?> ids) {
            ids.stream().filter(Number.class::isInstance).map(Number.class::cast).map(Number::longValue)
                    .map(NotifyRecipientDTO::admin).forEach(result::add);
        }
        return result;
    }
    @Override public Map<String, Object> resolveVariables(NotifyBusinessEvent event, NotifyRecipientDTO recipient) {
        Map<String, Object> result = new LinkedHashMap<>(event.getPayload()); result.put("event.time", event.getOccurredAt()); return result;
    }
    private NotifySceneRespDTO scene(String code, String name, String... roles) {
        return new NotifySceneRespDTO(code, name, List.of(
                new NotifySceneVariableRespDTO("withdrawal.id", "提现单编号", false),
                new NotifySceneVariableRespDTO("withdrawal.amount", "提现金额", false),
                new NotifySceneVariableRespDTO("pendingCount", "待审核笔数", false),
                new NotifySceneVariableRespDTO("approvedCount", "待打款笔数", false),
                new NotifySceneVariableRespDTO("approvedAmount", "待打款金额", false),
                new NotifySceneVariableRespDTO("overdueCount", "超时未完成笔数", false),
                new NotifySceneVariableRespDTO(NOTIFICATION_REJECTION_REASON, "驳回原因", false)),
                Arrays.stream(roles).map(role -> new NotifySceneRoleRespDTO(role,
                        ROLE_FINANCE.equals(role) ? "财务" : "申请人")).toList(),
                List.of(NotifyActionType.NONE, NotifyActionType.BUSINESS_DETAIL), false);
    }
}
