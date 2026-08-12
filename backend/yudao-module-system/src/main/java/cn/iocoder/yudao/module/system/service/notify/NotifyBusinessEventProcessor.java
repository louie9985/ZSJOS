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
import java.util.List;
import java.util.Set;
import cn.iocoder.yudao.module.system.api.notify.NotifyChannelType;
import cn.iocoder.yudao.module.system.api.notify.NotifyChannelAdapter;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyDeliveryContext;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySendResult;

@Service
@Slf4j
public class NotifyBusinessEventProcessor {

    @Resource private NotifySceneRegistry sceneRegistry;
    @Resource private NotifyRuleService notifyRuleService;
    @Resource private NotifyTemplateService notifyTemplateService;
    @Resource private NotifyBusinessMessageCreator messageCreator;
    @Resource private List<NotifyChannelAdapter> channelAdapters;

    public void process(NotifyBusinessEvent event) {
        TenantUtils.execute(event.getTenantId(), () -> processInTenant(event));
    }

    private void processInTenant(NotifyBusinessEvent event) {
        NotifySceneProvider provider = sceneRegistry.getProvider(event.getSceneCode());
        if (provider == null) {
            return;
        }
        for (NotifyRuleDO rule : notifyRuleService.getEnabledRules(event.getSceneCode())) {
            if (event.getTargetRuleId() != null && !event.getTargetRuleId().equals(rule.getId())) {
                continue;
            }
            try {
                String channelCode = rule.getChannelCode() == null || rule.getChannelCode().isBlank()
                        ? NotifyChannelType.IN_APP : rule.getChannelCode();
                if (NotifyChannelType.WEBSOCKET.equals(channelCode)) {
                    // WebSocket is emitted by the durable in-app message AFTER_COMMIT listener.
                    continue;
                }
                Set<Long> recipients = new LinkedHashSet<>(rule.getSpecifiedUserIds());
                recipients.addAll(provider.resolveRecipients(event, new LinkedHashSet<>(rule.getRecipientRoles())));
                NotifyTemplateDO template = notifyTemplateService.getNotifyTemplate(rule.getTemplateId());
                boolean websocketUsesInAppTemplate = template != null && NotifyChannelType.WEBSOCKET.equals(channelCode)
                        && NotifyChannelType.IN_APP.equals(template.getChannelCode());
                if (template == null || !event.getSceneCode().equals(template.getSceneCode())
                        || (template.getChannelCode() != null && !channelCode.equals(template.getChannelCode())
                        && !websocketUsesInAppTemplate)) {
                    continue;
                }
                for (Long recipientId : recipients) {
                    // WebSocket is bound to the durable in-app message lifecycle.
                    // The AFTER_COMMIT listener pushes it when the recipient is online.
                    if (NotifyChannelType.IN_APP.equals(channelCode)) {
                        createMessageBestEffort(event, provider, rule, template, recipientId);
                    } else {
                        sendExternalBestEffort(event, provider, rule, template, recipientId, channelCode);
                    }
                }
            } catch (Exception exception) {
                log.warn("[processInTenant][scene({}) ruleId({}) recipient resolution failed]",
                        event.getSceneCode(), rule.getId(), exception);
            }
        }
    }

    private void sendExternalBestEffort(NotifyBusinessEvent event, NotifySceneProvider provider,
                                        NotifyRuleDO rule, NotifyTemplateDO template, Long recipientId,
                                        String channelCode) {
        try {
            var variables = provider.resolveVariables(event, recipientId);
            var adapter = channelAdapters.stream().filter(item -> channelCode.equals(item.getChannelCode()))
                    .findFirst().orElse(null);
            if (adapter == null) {
                log.warn("[sendExternalBestEffort][channel({}) adapter missing]", channelCode);
                return;
            }
            NotifySendResult result = adapter.send(NotifyDeliveryContext.builder()
                    .tenantId(event.getTenantId()).sceneCode(event.getSceneCode()).sourceEventKey(event.getSourceEventKey())
                    .ruleId(rule.getId()).userId(recipientId).userType(2).templateCode(template.getCode())
                    .smsTemplateId(template.getSmsTemplateId()).wecomMessageType(template.getWecomMessageType())
                    .title(notifyTemplateService.formatNotifyTemplateContent(template.getTitle(), variables))
                    .content(notifyTemplateService.formatNotifyTemplateContent(template.getContent(), variables))
                    .variables(variables).bizType(event.getBizType()).bizId(event.getBizId()).build());
            if (!result.isSuccess()) {
                log.warn("[sendExternalBestEffort][channel({}) scene({}) failed code({})]",
                        channelCode, event.getSceneCode(), result.getErrorCode());
            }
        } catch (Exception exception) {
            log.warn("[sendExternalBestEffort][channel({}) scene({}) failed]",
                    channelCode, event.getSceneCode(), exception);
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
