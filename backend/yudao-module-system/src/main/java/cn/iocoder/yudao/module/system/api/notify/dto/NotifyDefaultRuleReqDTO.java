package cn.iocoder.yudao.module.system.api.notify.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class NotifyDefaultRuleReqDTO {
    String name;
    String sceneCode;
    String templateCode;
    List<String> recipientRoles;
    String actionType;
    String timingStage;
    Integer timingOffsetMinutes;
}
