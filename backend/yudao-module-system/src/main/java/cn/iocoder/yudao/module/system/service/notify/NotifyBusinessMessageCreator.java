package cn.iocoder.yudao.module.system.service.notify;

import cn.iocoder.yudao.module.system.api.notify.NotifySceneProvider;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyRecipientDTO;
import cn.iocoder.yudao.module.system.dal.dataobject.notify.NotifyRuleDO;
import cn.iocoder.yudao.module.system.dal.dataobject.notify.NotifyTemplateDO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class NotifyBusinessMessageCreator {

    @Resource private NotifyTemplateService notifyTemplateService;
    @Resource private NotifyMessageService notifyMessageService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void create(NotifyBusinessEvent event, NotifySceneProvider provider, NotifyRuleDO rule,
                       NotifyTemplateDO template, NotifyRecipientDTO recipient) {
        if (notifyMessageService.existsByRuleUserAndEvent(rule.getId(), recipient.getUserId(),
                recipient.getUserType(), event.getSourceEventKey())) {
            return;
        }
        Map<String, Object> variables = provider.resolveVariables(event, recipient);
        String title = notifyTemplateService.formatNotifyTemplateContent(template.getTitle(), variables);
        String summary = notifyTemplateService.formatNotifyTemplateContent(template.getSummary(), variables);
        String content = notifyTemplateService.formatNotifyTemplateContent(template.getContent(), variables);
        notifyMessageService.createNotifyMessage(NotifyMessageCreateReqDTO.builder()
                .userId(recipient.getUserId()).userType(recipient.getUserType()).template(template)
                .title(title).summary(summary).content(content).templateParams(variables)
                .notifyRuleId(rule.getId()).sceneCode(event.getSceneCode())
                .sourceEventKey(event.getSourceEventKey()).actionType(rule.getActionType())
                .bizType(event.getBizType()).bizId(event.getBizId()).build());
    }
}
