package cn.iocoder.yudao.module.zsjos.service.pointsredemption;
import cn.iocoder.yudao.module.system.api.notify.*;
import cn.iocoder.yudao.module.system.api.notify.dto.*;
import org.springframework.stereotype.Component;
import java.util.*;
@Component public class PointsNotifySceneProvider implements NotifySceneProvider {
    public List<NotifySceneRespDTO> getScenes() {
        return List.of(new NotifySceneRespDTO("zsjos.points-redemption.issued", "积分兑换码已生成",
                List.of(new NotifySceneVariableRespDTO("requestNo", "申请编号", false),
                        new NotifySceneVariableRespDTO("creditsAmount", "积分数", false),
                        new NotifySceneVariableRespDTO("deepLink", "申请详情", false)),
                List.of(new NotifySceneRoleRespDTO("applicant", "申请人")), List.of(NotifyActionType.BUSINESS_DETAIL), false),
                new NotifySceneRespDTO(PointsApprovalProgressService.URGE_SCENE, "积分申请审批催办",
                        List.of(new NotifySceneVariableRespDTO("requestNo", "申请编号", false),
                                new NotifySceneVariableRespDTO("applicantName", "申请人", false),
                                new NotifySceneVariableRespDTO("taskName", "当前审批节点", false),
                                new NotifySceneVariableRespDTO("taskId", "审批任务", false),
                                new NotifySceneVariableRespDTO("processInstanceId", "审批流程", false),
                                new NotifySceneVariableRespDTO("deepLink", "审批入口", false)),
                        List.of(new NotifySceneRoleRespDTO("approver", "当前审批人")), List.of(NotifyActionType.BUSINESS_DETAIL), false));
    }
    public Set<NotifyRecipientDTO> resolveRecipients(NotifyBusinessEvent event, Set<String> roles) {
        return new LinkedHashSet<>(event.validatedFixedRecipients());
    }
    public Map<String,Object> resolveVariables(NotifyBusinessEvent event, NotifyRecipientDTO recipient) { return event.getPayload(); }
}
