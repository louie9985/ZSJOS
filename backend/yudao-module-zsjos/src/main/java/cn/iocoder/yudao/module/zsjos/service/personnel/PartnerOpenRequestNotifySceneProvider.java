package cn.iocoder.yudao.module.zsjos.service.personnel;

import cn.iocoder.yudao.module.system.api.notify.NotifyActionType;
import cn.iocoder.yudao.module.system.api.notify.NotifySceneProvider;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyRecipientDTO;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySceneRespDTO;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySceneRoleRespDTO;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySceneVariableRespDTO;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.module.zsjos.enums.PersonnelConstants.*;

@Component
public class PartnerOpenRequestNotifySceneProvider implements NotifySceneProvider {

    private static final String ROLE_APPLICANT = "applicant";
    private static final String ROLE_REVIEWER = "reviewer";

    @Override
    public List<NotifySceneRespDTO> getScenes() {
        return List.of(
                scene(PARTNER_OPEN_REQUEST_SCENE_SUBMITTED, "代开通兼职账号待审批", ROLE_REVIEWER),
                scene(PARTNER_OPEN_REQUEST_SCENE_OPENED, "代开通兼职账号已生成邀请码", ROLE_APPLICANT),
                scene(PARTNER_OPEN_REQUEST_SCENE_REJECTED, "代开通兼职账号审批驳回", ROLE_APPLICANT),
                scene(PARTNER_OPEN_REQUEST_SCENE_OPEN_FAILED, "代开通兼职账号开通失败", ROLE_APPLICANT)
        );
    }

    @Override
    public Set<NotifyRecipientDTO> resolveRecipients(NotifyBusinessEvent event, Set<String> roles) {
        Set<NotifyRecipientDTO> result = new LinkedHashSet<>();
        Map<String, Object> payload = event.getPayload();
        if (roles.contains(ROLE_APPLICANT) && payload.get("applicantUserId") instanceof Number userId) {
            result.add(NotifyRecipientDTO.admin(userId.longValue()));
        }
        if (roles.contains(ROLE_REVIEWER) && payload.get("reviewerUserIds") instanceof Collection<?> ids) {
            ids.stream().filter(Number.class::isInstance).map(Number.class::cast)
                    .map(Number::longValue).map(NotifyRecipientDTO::admin).forEach(result::add);
        }
        return result;
    }

    @Override
    public Map<String, Object> resolveVariables(NotifyBusinessEvent event, NotifyRecipientDTO recipient) {
        Map<String, Object> result = new LinkedHashMap<>(event.getPayload());
        result.put("event.time", event.getOccurredAt());
        return result;
    }

    private NotifySceneRespDTO scene(String code, String name, String... roles) {
        return new NotifySceneRespDTO(code, name, List.of(
                new NotifySceneVariableRespDTO("request.id", "申请内部ID", false),
                new NotifySceneVariableRespDTO("request.no", "申请编号", false),
                new NotifySceneVariableRespDTO("partner.name", "兼职姓名", false),
                new NotifySceneVariableRespDTO("partner.mobile.masked", "脱敏手机号", false),
                new NotifySceneVariableRespDTO("assigned.employee", "归属员工", false),
                new NotifySceneVariableRespDTO("invite.code", "邀请码", true),
                new NotifySceneVariableRespDTO("invite.expiresAt", "邀请码过期时间", false),
                new NotifySceneVariableRespDTO("review.reason", "审批意见", false),
                new NotifySceneVariableRespDTO("failure.reason", "失败原因", false)
        ), Arrays.stream(roles)
                .map(role -> new NotifySceneRoleRespDTO(role, ROLE_REVIEWER.equals(role) ? "审批人" : "发起人"))
                .toList(), List.of(NotifyActionType.NONE, NotifyActionType.BUSINESS_DETAIL), false);
    }
}
