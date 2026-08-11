package cn.iocoder.yudao.module.zsjos.service.order;

import cn.iocoder.yudao.module.system.api.notify.NotifyActionType;
import cn.iocoder.yudao.module.system.api.notify.NotifySceneProvider;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySceneRespDTO;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySceneRoleRespDTO;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySceneVariableRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
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

    @Override
    public List<NotifySceneRespDTO> getScenes() {
        return List.of(scene(SUBMITTED, "成交订单待审批", ROLE_REVIEWERS),
                scene(EFFECTIVE, "成交订单已生效", ROLE_SUBMITTER),
                scene(REJECTED, "成交订单审批拒绝", ROLE_SUBMITTER),
                scene(CANCELLED, "成交订单审批取消", ROLE_SUBMITTER));
    }

    @Override
    public Set<Long> resolveRecipients(NotifyBusinessEvent event, Set<String> recipientRoles) {
        Set<Long> result = new LinkedHashSet<>();
        if (recipientRoles.contains(ROLE_REVIEWERS)) addIds(result, event.getPayload().get("reviewerUserIds"));
        if (recipientRoles.contains(ROLE_SUBMITTER)) addId(result, event.getPayload().get("submitterUserId"));
        return result;
    }

    @Override
    public Map<String, Object> resolveVariables(NotifyBusinessEvent event, Long recipientUserId) {
        SalesOrderDO order = orderMapper.selectById(event.getBizId());
        if (order == null) return Map.of();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("order.id", order.getId()); values.put("order.no", order.getOrderNo());
        values.put("order.studentName", order.getStudentName()); values.put("order.amount", order.getTotalAmount());
        values.put("order.status", order.getStatus()); values.put("order.submittedAt", order.getSubmittedAt());
        values.put("order.approvalDepartments", event.getPayload().get("approvalDepartments"));
        values.put("order.decisionReason", event.getPayload().get("decisionReason"));
        return values;
    }

    private NotifySceneRespDTO scene(String code, String name, String role) {
        return new NotifySceneRespDTO(code, name, List.of(
                new NotifySceneVariableRespDTO("order.id", "订单编号", false),
                new NotifySceneVariableRespDTO("order.no", "订单号", false),
                new NotifySceneVariableRespDTO("order.studentName", "学员姓名", true),
                new NotifySceneVariableRespDTO("order.amount", "成交金额", false),
                new NotifySceneVariableRespDTO("order.status", "订单状态", false),
                new NotifySceneVariableRespDTO("order.submittedAt", "提交时间", false),
                new NotifySceneVariableRespDTO("order.approvalDepartments", "审批部门", false),
                new NotifySceneVariableRespDTO("order.decisionReason", "审批理由", false)),
                List.of(new NotifySceneRoleRespDTO(role, ROLE_REVIEWERS.equals(role) ? "本轮实际审批人" : "订单提交销售")),
                List.of(NotifyActionType.NONE, NotifyActionType.MESSAGE_DETAIL, NotifyActionType.BUSINESS_DETAIL), false);
    }

    private void addIds(Set<Long> result, Object value) {
        if (value instanceof Collection<?> values) values.forEach(item -> addId(result, item));
    }

    private void addId(Set<Long> result, Object value) {
        if (value instanceof Number number && number.longValue() > 0) result.add(number.longValue());
    }
}
