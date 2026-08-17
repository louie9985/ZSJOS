package cn.iocoder.yudao.module.zsjos.service.order;

import cn.iocoder.yudao.module.system.api.notify.NotifyActionType;
import cn.iocoder.yudao.module.system.api.notify.NotifySceneProvider;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySceneRespDTO;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySceneRoleRespDTO;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySceneVariableRespDTO;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyRecipientDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.personnel.PartnerAccountMapper;
import cn.iocoder.yudao.module.zsjos.service.cashback.CashbackService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.module.zsjos.enums.SalesOrderNotifySceneConstants.*;

@Component
public class SalesOrderNotifySceneProvider implements NotifySceneProvider {
    @Resource private SalesOrderMapper orderMapper;
    @Resource private CashbackService cashbackService;
    @Resource private PartnerAccountMapper partnerAccountMapper;

    @Override
    public List<NotifySceneRespDTO> getScenes() {
        return List.of(scene(SUBMITTED, "成交订单待审批", ROLE_REVIEWERS),
                scene(SUBMITTER_PENDING, "客资进入成交审批", ROLE_LEAD_SUBMITTER),
                scene(EFFECTIVE, "成交订单已生效", ROLE_RESULT_ACTORS),
                scene(SUBMITTER_EFFECTIVE, "客资成交结果", ROLE_LEAD_SUBMITTER),
                scene(REJECTED, "成交订单审批拒绝", ROLE_RESULT_ACTORS),
                scene(CANCELLED, "成交订单审批取消", ROLE_RESULT_ACTORS),
                scene(SUPERVISOR_REQUESTED, "成交订单申请主管确认", ROLE_SUPERVISOR),
                scene(SUPERVISOR_DECIDED, "成交订单主管确认结果", ROLE_REQUESTER));
    }

    @Override
    public Set<NotifyRecipientDTO> resolveRecipients(NotifyBusinessEvent event, Set<String> recipientRoles) {
        Set<NotifyRecipientDTO> result = new LinkedHashSet<>();
        if (recipientRoles.contains(ROLE_REVIEWERS)) addIds(result, event.getPayload().get("reviewerUserIds"));
        if (recipientRoles.contains(ROLE_SUBMITTER)) addId(result, event.getPayload().get("submitterUserId"));
        if (recipientRoles.contains(ROLE_RESULT_ACTORS)) addIds(result, event.getPayload().get("resultUserIds"));
        if (recipientRoles.contains(ROLE_LEAD_SUBMITTER)) {
            if (event.getPayload().get("partnerId") instanceof Number number) {
                PartnerAccountDO account = partnerAccountMapper.selectByPartnerId(number.longValue());
                if (account != null) result.add(NotifyRecipientDTO.partner(account.getId()));
            } else {
                addId(result, event.getPayload().get("leadSubmitterUserId"));
            }
        }
        if (recipientRoles.contains(ROLE_SUPERVISOR)) addId(result, event.getPayload().get("supervisorUserId"));
        if (recipientRoles.contains(ROLE_REQUESTER)) addId(result, event.getPayload().get("requesterUserId"));
        return result;
    }

    @Override
    public Map<String, Object> resolveVariables(NotifyBusinessEvent event, NotifyRecipientDTO recipient) {
        SalesOrderDO order = orderMapper.selectById(event.getBizId());
        if (order == null) return Map.of();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("order.id", order.getId()); values.put("order.no", order.getOrderNo());
        values.put("order.studentName", order.getStudentName()); values.put("order.amount", order.getTotalAmount());
        values.put("order.status", order.getStatus()); values.put("order.submittedAt", order.getSubmittedAt());
        values.put("order.approvalDepartments", event.getPayload().get("approvalDepartments"));
        values.put("order.decisionReason", event.getPayload().get("decisionReason"));
        Long leadSubmitterUserId = event.getPayload().get("leadSubmitterUserId") instanceof Number number
                ? number.longValue() : null;
        Long partnerId = event.getPayload().get("partnerId") instanceof Number number ? number.longValue() : null;
        if (SUBMITTER_EFFECTIVE.equals(event.getSceneCode())) {
            values.put("order.cashbackTotal", partnerId != null
                    ? cashbackService.getPartnerOrderCashbackTotal(order.getId(), partnerId)
                    : cashbackService.getOrderCashbackTotal(order.getId(), leadSubmitterUserId));
        }
        values.put("order.supervisorReason", event.getPayload().get("supervisorReason"));
        return values;
    }

    private NotifySceneRespDTO scene(String code, String name, String... roles) {
        return new NotifySceneRespDTO(code, name, List.of(
                new NotifySceneVariableRespDTO("order.id", "订单编号", false),
                new NotifySceneVariableRespDTO("order.no", "订单号", false),
                new NotifySceneVariableRespDTO("order.studentName", "学员姓名", true),
                new NotifySceneVariableRespDTO("order.amount", "成交金额", false),
                new NotifySceneVariableRespDTO("order.status", "订单状态", false),
                new NotifySceneVariableRespDTO("order.submittedAt", "提交时间", false),
                new NotifySceneVariableRespDTO("order.approvalDepartments", "审批部门", false),
                new NotifySceneVariableRespDTO("order.decisionReason", "审批理由", false),
                new NotifySceneVariableRespDTO("order.cashbackTotal", "订单返现合计", false),
                new NotifySceneVariableRespDTO("order.supervisorReason", "主管确认原因或意见", false)),
                java.util.Arrays.stream(roles).map(role -> new NotifySceneRoleRespDTO(role, roleLabel(role))).toList(),
                List.of(NotifyActionType.NONE, NotifyActionType.MESSAGE_DETAIL, NotifyActionType.BUSINESS_DETAIL), false);
    }

    private String roleLabel(String role) {
        return switch (role) {
            case ROLE_REVIEWERS -> "本轮实际审批人";
            case ROLE_SUPERVISOR -> "直属部门负责人";
            case ROLE_REQUESTER -> "主管确认申请人";
            case ROLE_RESULT_ACTORS -> "正式销售和实际录单人";
            case ROLE_LEAD_SUBMITTER -> "客资提交人";
            default -> "订单提交销售";
        };
    }

    private void addIds(Set<NotifyRecipientDTO> result, Object value) {
        if (value instanceof Collection<?> values) values.forEach(item -> addId(result, item));
    }

    private void addId(Set<NotifyRecipientDTO> result, Object value) {
        if (value instanceof Number number && number.longValue() > 0) result.add(NotifyRecipientDTO.admin(number.longValue()));
    }
}
