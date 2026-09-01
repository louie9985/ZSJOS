package cn.iocoder.yudao.module.system.service.notify;

import cn.iocoder.yudao.module.system.dal.dataobject.notify.NotifyTemplateDO;
import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Value
@Builder
public class NotifyMessageCreateReqDTO {

    Long userId;
    Integer userType;
    NotifyTemplateDO template;
    String title;
    String summary;
    String content;
    Map<String, Object> templateParams;
    Long notifyRuleId;
    String sceneCode;
    String sourceEventKey;
    String actionType;
    String bizType;
    Long bizId;
}
