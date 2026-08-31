package cn.iocoder.yudao.module.zsjos.service.studentcontact;

import cn.iocoder.yudao.module.system.api.notify.NotifyActionType;
import cn.iocoder.yudao.module.system.api.notify.NotifySceneProvider;
import cn.iocoder.yudao.module.system.api.notify.dto.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.module.zsjos.service.studentcontact.StudentContactConstants.*;

@Component
public class StudentContactNotifySceneProvider implements NotifySceneProvider {

    @Resource private ServiceRelationMapper relationMapper;
    @Resource private SalesOrderMapper orderMapper;
    @Resource private LeadMapper leadMapper;

    @Override
    public List<NotifySceneRespDTO> getScenes() {
        return List.of(scene(NOTIFY_FIRST_CONTACT, "学员首次联系时限提醒"),
                scene(NOTIFY_STUDY_PLAN, "学员学习计划时限提醒"),
                scene(NOTIFY_CONTACT, "学员普通联系提醒"),
                scene(NOTIFY_EXAM_NOTICE, "学员考前通知提醒"),
                scene(NOTIFY_OPERATOR_ASSIGNED, "学员运营指派通知"));
    }

    @Override
    public Set<NotifyRecipientDTO> resolveRecipients(NotifyBusinessEvent event, Set<String> roles) {
        Map<String, Object> payload = event.getPayload() == null ? Map.of() : event.getPayload();
        Set<NotifyRecipientDTO> recipients = new LinkedHashSet<>();
        if (roles.contains(NOTIFY_ROLE_PLANNER)) add(recipients, payload.get("plannerUserId"));
        if (roles.contains(NOTIFY_ROLE_SUPERVISOR)) add(recipients, payload.get("supervisorUserId"));
        if (roles.contains(NOTIFY_ROLE_OPERATOR)) add(recipients, payload.get("operatorUserId"));
        return recipients;
    }

    @Override
    public Map<String, Object> resolveVariables(NotifyBusinessEvent event, NotifyRecipientDTO recipient) {
        ServiceRelationDO relation = relationMapper.selectById(event.getBizId());
        SalesOrderDO order = relation == null ? null : orderMapper.selectById(relation.getOrderId());
        LeadDO lead = order == null || order.getLeadId() == null ? null : leadMapper.selectById(order.getLeadId());
        Map<String, Object> payload = event.getPayload() == null ? Map.of() : event.getPayload();
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("student.identifier", lead != null && lead.getLeadNo() != null ? lead.getLeadNo()
                : order == null ? "" : order.getOrderNo());
        variables.put("contact.stage", payload.get("reminder.stage"));
        variables.put("contact.dueAt", payload.get("reminder.dueAt"));
        variables.put("exam.date", payload.get("examDate"));
        return variables;
    }

    private NotifySceneRespDTO scene(String code, String name) {
        return new NotifySceneRespDTO(code, name, List.of(
                new NotifySceneVariableRespDTO("student.identifier", "客资编号或订单号", false),
                new NotifySceneVariableRespDTO("contact.stage", "提醒阶段", false),
                new NotifySceneVariableRespDTO("contact.dueAt", "任务截止时间", false),
                new NotifySceneVariableRespDTO("exam.date", "考试日期", false)),
                List.of(new NotifySceneRoleRespDTO(NOTIFY_ROLE_PLANNER, "学习规划师"),
                        new NotifySceneRoleRespDTO(NOTIFY_ROLE_SUPERVISOR, "教务主管"),
                        new NotifySceneRoleRespDTO(NOTIFY_ROLE_OPERATOR, "运营负责人")),
                List.of(NotifyActionType.MESSAGE_DETAIL, NotifyActionType.BUSINESS_DETAIL), true);
    }

    private void add(Set<NotifyRecipientDTO> recipients, Object value) {
        if (value instanceof Number number && number.longValue() > 0) {
            recipients.add(NotifyRecipientDTO.admin(number.longValue()));
        }
    }
}
