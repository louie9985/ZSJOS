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
import java.util.concurrent.atomic.AtomicReference;
import cn.iocoder.yudao.module.system.api.notify.NotifyChannelType;
import cn.iocoder.yudao.module.system.api.notify.NotifyChannelAdapter;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyDeliveryContext;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySendResult;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyRecipientDTO;

@Service
@Slf4j
public class NotifyBusinessEventProcessor {

    @Resource private NotifySceneRegistry sceneRegistry;
    @Resource private NotifyRuleService notifyRuleService;
    @Resource private NotifyTemplateService notifyTemplateService;
    @Resource private NotifyBusinessMessageCreator messageCreator;
    @org.springframework.beans.factory.annotation.Autowired private List<NotifyChannelAdapter> channelAdapters;

    public void process(NotifyBusinessEvent event) {
        TenantUtils.execute(event.getTenantId(), () -> processInTenant(event));
    }

    public NotifySendResult processConfirmed(NotifyBusinessEvent event) {
        AtomicReference<NotifySendResult> result = new AtomicReference<>();
        TenantUtils.execute(event.getTenantId(), () -> result.set(processConfirmedInTenant(event)));
        return result.get();
    }

    private NotifySendResult processConfirmedInTenant(NotifyBusinessEvent event) {
        NotifySceneProvider provider = sceneRegistry.getProvider(event.getSceneCode());
        if (provider == null) {
            return NotifySendResult.failure("NOTIFY_SCENE_MISSING", "通知场景未注册", false);
        }
        List<NotifyRuleDO> rules = notifyRuleService.getEnabledRules(event.getSceneCode()).stream()
                .filter(rule -> event.getTargetRuleId() == null || event.getTargetRuleId().equals(rule.getId()))
                .toList();
        if (rules.isEmpty()) {
            return NotifySendResult.failure("NOTIFY_RULE_MISSING", "未找到启用的通知规则", false);
        }
        try {
            for (NotifyRuleDO rule : rules) {
                NotifySendResult ruleResult = deliverConfirmed(event, provider, rule);
                if (!ruleResult.isSuccess()) {
                    return ruleResult;
                }
            }
            return NotifySendResult.success(null);
        } catch (Exception exception) {
            log.warn("[processConfirmedInTenant][scene({}) targetRuleId({}) failed]",
                    event.getSceneCode(), event.getTargetRuleId(), exception);
            return NotifySendResult.failure("NOTIFY_DELIVERY_FAILED", "通知投递失败", true);
        }
    }

    private NotifySendResult deliverConfirmed(NotifyBusinessEvent event, NotifySceneProvider provider,
                                              NotifyRuleDO rule) {
        String channelCode = rule.getChannelCode() == null || rule.getChannelCode().isBlank()
                ? NotifyChannelType.IN_APP : rule.getChannelCode();
        if (NotifyChannelType.WEBSOCKET.equals(channelCode)) {
            return NotifySendResult.failure("NOTIFY_CHANNEL_UNSUPPORTED", "WebSocket 不能作为独立可靠投递渠道", false);
        }
        NotifyTemplateDO template = notifyTemplateService.getNotifyTemplate(rule.getTemplateId());
        if (template == null || !event.getSceneCode().equals(template.getSceneCode())
                || template.getChannelCode() != null && !channelCode.equals(template.getChannelCode())) {
            return NotifySendResult.failure("NOTIFY_TEMPLATE_INVALID", "通知模板与规则不匹配", false);
        }
        NotifySendResult contractFailure = validateTemplateContract(event, template);
        if (contractFailure != null) return contractFailure;
        Set<NotifyRecipientDTO> recipients = new LinkedHashSet<>();
        rule.getSpecifiedUserIds().stream().map(NotifyRecipientDTO::admin).forEach(recipients::add);
        recipients.addAll(provider.resolveRecipients(event, new LinkedHashSet<>(rule.getRecipientRoles())));
        if (recipients.isEmpty()) {
            return NotifySendResult.failure("NOTIFY_RECIPIENT_MISSING", "通知收件人暂不可用", true);
        }
        for (NotifyRecipientDTO recipient : recipients) {
            if (NotifyChannelType.IN_APP.equals(channelCode)) {
                try {
                    messageCreator.create(event, provider, rule, template, recipient);
                } catch (DuplicateKeyException ignored) {
                    // The durable message already exists, so this retry is confirmed successful.
                }
                continue;
            }
            var adapter = channelAdapters.stream().filter(item -> channelCode.equals(item.getChannelCode()))
                    .findFirst().orElse(null);
            if (adapter == null) {
                return NotifySendResult.failure("NOTIFY_CHANNEL_MISSING", "通知渠道未配置", false);
            }
            var variables = provider.resolveVariables(event, recipient);
            NotifySendResult result = adapter.send(NotifyDeliveryContext.builder()
                    .tenantId(event.getTenantId()).sceneCode(event.getSceneCode()).sourceEventKey(event.getSourceEventKey())
                    .ruleId(rule.getId()).actionType(rule.getActionType()).userId(recipient.getUserId())
                    .userType(recipient.getUserType()).templateCode(template.getCode())
                    .smsTemplateId(template.getSmsTemplateId()).wecomMessageType(template.getWecomMessageType())
                    .title(notifyTemplateService.formatNotifyTemplateContent(template.getTitle(), variables))
                    .content(notifyTemplateService.formatNotifyTemplateContent(template.getContent(), variables))
                    .variables(variables).bizType(event.getBizType()).bizId(event.getBizId()).build());
            if (!result.isSuccess()) {
                return result;
            }
        }
        return NotifySendResult.success(null);
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
                Set<NotifyRecipientDTO> recipients = new LinkedHashSet<>();
                rule.getSpecifiedUserIds().stream().map(NotifyRecipientDTO::admin).forEach(recipients::add);
                recipients.addAll(provider.resolveRecipients(event, new LinkedHashSet<>(rule.getRecipientRoles())));
                NotifyTemplateDO template = notifyTemplateService.getNotifyTemplate(rule.getTemplateId());
                boolean websocketUsesInAppTemplate = template != null && NotifyChannelType.WEBSOCKET.equals(channelCode)
                        && NotifyChannelType.IN_APP.equals(template.getChannelCode());
                if (template == null || !event.getSceneCode().equals(template.getSceneCode())
                        || (template.getChannelCode() != null && !channelCode.equals(template.getChannelCode())
                        && !websocketUsesInAppTemplate)) {
                    continue;
                }
                if (hasInvalidTemplateContract(event, template)) {
                    log.error("[processInTenant][scene({}) template({}) violates scene variable contract]",
                            event.getSceneCode(), template.getCode());
                    continue;
                }
                for (NotifyRecipientDTO recipient : recipients) {
                    // WebSocket is bound to the durable in-app message lifecycle.
                    // The AFTER_COMMIT listener pushes it when the recipient is online.
                    if (NotifyChannelType.IN_APP.equals(channelCode)) {
                        createMessageBestEffort(event, provider, rule, template, recipient);
                    } else {
                        sendExternalBestEffort(event, provider, rule, template, recipient, channelCode);
                    }
                }
            } catch (Exception exception) {
                log.warn("[processInTenant][scene({}) ruleId({}) recipient resolution failed]",
                        event.getSceneCode(), rule.getId(), exception);
            }
        }
    }

    private NotifySendResult validateTemplateContract(NotifyBusinessEvent event, NotifyTemplateDO template) {
        Set<String> invalid = invalidTemplateParams(event, template);
        if (invalid.isEmpty()) return null;
        return NotifySendResult.failure("NOTIFY_TEMPLATE_PARAM_INVALID",
                "通知模板包含不属于业务场景的变量", false);
    }

    private boolean hasInvalidTemplateContract(NotifyBusinessEvent event, NotifyTemplateDO template) {
        return !invalidTemplateParams(event, template).isEmpty();
    }

    private Set<String> invalidTemplateParams(NotifyBusinessEvent event, NotifyTemplateDO template) {
        List<String> params = new java.util.ArrayList<>(template.getParams() == null
                ? List.of() : template.getParams());
        params.addAll(notifyTemplateService.parseTemplateParams(template.getTitle(), template.getSummary(),
                template.getContent()));
        return sceneRegistry.findInvalidTemplateParams(event.getSceneCode(), params);
    }

    private void sendExternalBestEffort(NotifyBusinessEvent event, NotifySceneProvider provider,
                                        NotifyRuleDO rule, NotifyTemplateDO template, NotifyRecipientDTO recipient,
                                        String channelCode) {
        try {
            var variables = provider.resolveVariables(event, recipient);
            var adapter = channelAdapters.stream().filter(item -> channelCode.equals(item.getChannelCode()))
                    .findFirst().orElse(null);
            if (adapter == null) {
                log.warn("[sendExternalBestEffort][channel({}) adapter missing]", channelCode);
                return;
            }
            NotifySendResult result = adapter.send(NotifyDeliveryContext.builder()
                    .tenantId(event.getTenantId()).sceneCode(event.getSceneCode()).sourceEventKey(event.getSourceEventKey())
                    .ruleId(rule.getId()).actionType(rule.getActionType()).userId(recipient.getUserId())
                    .userType(recipient.getUserType()).templateCode(template.getCode())
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
                                         NotifyRuleDO rule, NotifyTemplateDO template, NotifyRecipientDTO recipient) {
        try {
            messageCreator.create(event, provider, rule, template, recipient);
        } catch (DuplicateKeyException exception) {
            // A concurrent delivery of the same rule/user/event is already persisted.
            log.debug("[createMessageBestEffort][scene({}) ruleId({}) userId({}) duplicate ignored]",
                    event.getSceneCode(), rule.getId(), recipient.getUserId());
        } catch (Exception exception) {
            log.warn("[createMessageBestEffort][scene({}) ruleId({}) userId({}) creation failed]",
                    event.getSceneCode(), rule.getId(), recipient.getUserId(), exception);
        }
    }
}
