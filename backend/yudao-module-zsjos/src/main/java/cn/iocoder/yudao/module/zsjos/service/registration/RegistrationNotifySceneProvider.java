package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.module.system.api.notify.NotifyActionType;
import cn.iocoder.yudao.module.system.api.notify.NotifySceneProvider;
import cn.iocoder.yudao.module.system.api.notify.dto.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.module.zsjos.service.registration.RegistrationConstants.*;

@Component
public class RegistrationNotifySceneProvider implements NotifySceneProvider {

    @Resource private PermissionApi permissionApi;
    @Resource private AdminUserApi adminUserApi;
    @Resource private cn.iocoder.yudao.module.zsjos.dal.mysql.registration.RegistrationCaseMapper cases;
    @Resource private cn.iocoder.yudao.module.zsjos.dal.mysql.studentinfo.StudentInfoFormMapper forms;

    @Override
    public List<NotifySceneRespDTO> getScenes() {
        return List.of(new NotifySceneRespDTO(NOTIFY_SCENE_INFO_SUBMITTED, "报名信息收集表已填写", List.of(
                new NotifySceneVariableRespDTO("student.name", "学员姓名", false)),
                List.of(new NotifySceneRoleRespDTO(NOTIFY_ROLE_POOL_HANDLERS, "报名履约任务负责人")),
                List.of(NotifyActionType.MESSAGE_DETAIL), false), new NotifySceneRespDTO(NOTIFY_SCENE_DELIVERY_CANCELLED, "提前交付已停止", List.of(
                new NotifySceneVariableRespDTO("registration.caseId", "报名履约任务编号", false),
                new NotifySceneVariableRespDTO("reason", "停止原因", false)),
                List.of(new NotifySceneRoleRespDTO(NOTIFY_ROLE_HEAD_TEACHER, "班主任")),
                List.of(NotifyActionType.MESSAGE_DETAIL), false), new NotifySceneRespDTO(NOTIFY_SCENE_TASK_CREATED, "新报名履约任务", List.of(
                new NotifySceneVariableRespDTO("registration.caseId", "报名履约任务编号", false),
                new NotifySceneVariableRespDTO("order.id", "订单编号", false),
                new NotifySceneVariableRespDTO("order.no", "订单号", false)),
                List.of(new NotifySceneRoleRespDTO(NOTIFY_ROLE_POOL_HANDLERS, "报名履约任务负责人")),
                List.of(NotifyActionType.MESSAGE_DETAIL, NotifyActionType.BUSINESS_DETAIL), false),
                new NotifySceneRespDTO(NOTIFY_SCENE_PLANNER_ASSIGNED, "班主任分配", List.of(
                        new NotifySceneVariableRespDTO("registration.caseId", "报名履约任务编号", false),
                        new NotifySceneVariableRespDTO("order.no", "订单号", false),
                        new NotifySceneVariableRespDTO("student.name", "学员姓名", false),
                        new NotifySceneVariableRespDTO("student.no", "学员编号", false)),
                List.of(new NotifySceneRoleRespDTO(NOTIFY_ROLE_HEAD_TEACHER, "班主任")),
                List.of(NotifyActionType.MESSAGE_DETAIL, NotifyActionType.BUSINESS_DETAIL), false),
                new NotifySceneRespDTO(NOTIFY_SCENE_DIRECTOR_ASSIGNED, "编导学员分配", List.of(
                        new NotifySceneVariableRespDTO("registration.caseId", "报名履约任务编号", false),
                        new NotifySceneVariableRespDTO("order.no", "订单号", false),
                        new NotifySceneVariableRespDTO("lead.no", "客资编号", false)),
                List.of(new NotifySceneRoleRespDTO(NOTIFY_ROLE_CONTENT_DIRECTOR, "编导")),
                List.of(NotifyActionType.MESSAGE_DETAIL, NotifyActionType.BUSINESS_DETAIL), false));
    }

    @Override
    public Set<NotifyRecipientDTO> resolveRecipients(NotifyBusinessEvent event, Set<String> roles) {
        Map<String, Object> payload = event.getPayload() == null ? Map.of() : event.getPayload();
        if (roles.contains(NOTIFY_ROLE_HEAD_TEACHER)) {
            Object plannerId = payload.get("studyPlannerUserId");
            if (plannerId instanceof Number number) return Set.of(NotifyRecipientDTO.admin(number.longValue()));
            return Set.of();
        }
        if (roles.contains(NOTIFY_ROLE_CONTENT_DIRECTOR)) {
            Object directorId = payload.get("contentDirectorUserId");
            if (directorId instanceof Number number) return Set.of(NotifyRecipientDTO.admin(number.longValue()));
            return Set.of();
        }
        if (!roles.contains(NOTIFY_ROLE_POOL_HANDLERS)) return Set.of();
        var item = cases.selectById(event.getBizId());
        if (NOTIFY_SCENE_INFO_SUBMITTED.equals(event.getSceneCode())) {
            var form = forms.selectById(event.getBizId());
            item = form == null ? null : form.getRegistrationCaseId() != null
                    ? cases.selectById(form.getRegistrationCaseId())
                    : form.getOrderItemId() == null ? null : cases.selectByOrderItemId(form.getOrderItemId());
        }
        if (item == null || item.getOwnerUserId() == null) return Set.of();
        var owner = adminUserApi.getUser(item.getOwnerUserId());
        return owner != null && Integer.valueOf(0).equals(owner.getStatus())
                && permissionApi.hasAnyPermissions(owner.getId(), "zsjos:registration:query-my")
                ? Set.of(NotifyRecipientDTO.admin(owner.getId())) : Set.of();
    }

    @Override
    public Map<String, Object> resolveVariables(NotifyBusinessEvent event, NotifyRecipientDTO recipient) {
        Map<String, Object> payload = event.getPayload() == null ? Map.of() : event.getPayload();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reason", payload.get("reason"));
        result.put("registration.caseId", payload.get("registrationCaseId"));
        result.put("order.id", payload.get("orderId"));
        result.put("order.no", payload.get("orderNo"));
        if (!NOTIFY_SCENE_PLANNER_ASSIGNED.equals(event.getSceneCode())) {
            result.put("lead.no", payload.get("leadNo"));
        }
        result.put("student.name", payload.get("studentName"));
        result.put("student.no", payload.get("studentNo"));
        return result;
    }
}
