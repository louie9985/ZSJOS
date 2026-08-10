package cn.iocoder.yudao.module.system.service.notify;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.system.api.notify.NotifySceneProvider;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.system.dal.dataobject.notify.NotifyRuleDO;
import cn.iocoder.yudao.module.system.dal.dataobject.notify.NotifyTemplateDO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
@Slf4j
public class NotifyBusinessEventProcessor {

    @Resource private NotifySceneRegistry sceneRegistry;
    @Resource private NotifyRuleService notifyRuleService;
    @Resource private NotifyTemplateService notifyTemplateService;
    @Resource private NotifyBusinessMessageCreator messageCreator;

    public void process(NotifyBusinessEvent event) {
        TenantUtils.execute(event.getTenantId(), () -> processInTenant(event));
    }

    private void processInTenant(NotifyBusinessEvent event) {
        NotifySceneProvider provider = sceneRegistry.getProvider(event.getSceneCode());
        if (provider == null) {
            return;
        }
        for (NotifyRuleDO rule : notifyRuleService.getEnabledRules(event.getSceneCode())) {
            try {
                Set<Long> recipients = new LinkedHashSet<>(rule.getSpecifiedUserIds());
                recipients.addAll(provider.resolveRecipients(event, new LinkedHashSet<>(rule.getRecipientRoles())));
                NotifyTemplateDO template = notifyTemplateService.getNotifyTemplate(rule.getTemplateId());
                if (template == null || !event.getSceneCode().equals(template.getSceneCode())) {
                    continue;
                }
                for (Long recipientId : recipients) {
                    createMessageBestEffort(event, provider, rule, template, recipientId);
                }
            } catch (Exception exception) {
                log.warn("[processInTenant][scene({}) ruleId({}) recipient resolution failed]",
                        event.getSceneCode(), rule.getId(), exception);
            }
        }
    }

    private void createMessageBestEffort(NotifyBusinessEvent event, NotifySceneProvider provider,
                                         NotifyRuleDO rule, NotifyTemplateDO template, Long recipientId) {
        try {
            messageCreator.create(event, provider, rule, template, recipientId);
        } catch (DuplicateKeyException exception) {
            // A concurrent delivery of the same rule/user/event is already persisted.
            log.debug("[createMessageBestEffort][scene({}) ruleId({}) userId({}) duplicate ignored]",
                    event.getSceneCode(), rule.getId(), recipientId);
        } catch (Exception exception) {
            log.warn("[createMessageBestEffort][scene({}) ruleId({}) userId({}) creation failed]",
                    event.getSceneCode(), rule.getId(), recipientId, exception);
        }
    }
}
