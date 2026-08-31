package cn.iocoder.yudao.module.system.service.notify;

import cn.iocoder.yudao.module.system.api.notify.NotifySceneProvider;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyRecipientDTO;
import cn.iocoder.yudao.module.system.dal.dataobject.notify.NotifyRuleDO;
import cn.iocoder.yudao.module.system.dal.dataobject.notify.NotifyTemplateDO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class NotifyBusinessEventProcessorTest {

    @InjectMocks
    private NotifyBusinessEventProcessor processor;
    @Mock
    private NotifySceneRegistry sceneRegistry;
    @Mock
    private NotifyRuleService notifyRuleService;
    @Mock
    private NotifyTemplateService notifyTemplateService;
    @Mock
    private NotifyBusinessMessageCreator messageCreator;
    @Mock
    private NotifySceneProvider provider;

    @Test
    void processContinuesAfterOneRecipientFails() {
        NotifyBusinessEvent event = NotifyBusinessEvent.builder()
                .tenantId(10L).sceneCode("test.scene").sourceEventKey("event:1").build();
        NotifyRuleDO rule = NotifyRuleDO.builder().id(20L).sceneCode("test.scene").templateId(30L)
                .recipientRoles(List.of()).specifiedUserIds(List.of(100L, 200L)).build();
        NotifyTemplateDO template = NotifyTemplateDO.builder().id(30L).sceneCode("test.scene").build();
        when(sceneRegistry.getProvider("test.scene")).thenReturn(provider);
        when(notifyRuleService.getEnabledRules("test.scene")).thenReturn(List.of(rule));
        when(provider.resolveRecipients(event, Set.of())).thenReturn(Set.of());
        when(notifyTemplateService.getNotifyTemplate(30L)).thenReturn(template);
        doThrow(new IllegalStateException("render failed"))
                .when(messageCreator).create(event, provider, rule, template, NotifyRecipientDTO.admin(100L));

        processor.process(event);

        verify(messageCreator).create(event, provider, rule, template, NotifyRecipientDTO.admin(100L));
        verify(messageCreator).create(event, provider, rule, template, NotifyRecipientDTO.admin(200L));
    }

    @Test
    void confirmedDeliveryFailsWhenMessagePersistenceFails() {
        NotifyBusinessEvent event = NotifyBusinessEvent.builder()
                .tenantId(10L).sceneCode("test.scene").sourceEventKey("event:2").targetRuleId(20L).build();
        NotifyRuleDO rule = NotifyRuleDO.builder().id(20L).sceneCode("test.scene").templateId(30L)
                .channelCode("in_app").recipientRoles(List.of()).specifiedUserIds(List.of(100L)).build();
        NotifyTemplateDO template = NotifyTemplateDO.builder().id(30L).sceneCode("test.scene")
                .channelCode("in_app").build();
        when(sceneRegistry.getProvider("test.scene")).thenReturn(provider);
        when(notifyRuleService.getEnabledRules("test.scene")).thenReturn(List.of(rule));
        when(provider.resolveRecipients(event, Set.of())).thenReturn(Set.of());
        when(notifyTemplateService.getNotifyTemplate(30L)).thenReturn(template);
        doThrow(new IllegalStateException("database unavailable"))
                .when(messageCreator).create(event, provider, rule, template, NotifyRecipientDTO.admin(100L));

        var result = processor.processConfirmed(event);

        assertFalse(result.isSuccess());
        assertTrue(result.isRetryable());
    }

    @Test
    void confirmedDeliveryStopsWhenTemplateViolatesSceneVariableContract() {
        NotifyBusinessEvent event = NotifyBusinessEvent.builder()
                .tenantId(10L).sceneCode("test.scene").sourceEventKey("event:3").targetRuleId(20L).build();
        NotifyRuleDO rule = NotifyRuleDO.builder().id(20L).sceneCode("test.scene").templateId(30L)
                .channelCode("in_app").recipientRoles(List.of()).specifiedUserIds(List.of(100L)).build();
        NotifyTemplateDO template = NotifyTemplateDO.builder().id(30L).sceneCode("test.scene")
                .channelCode("in_app").params(List.of("lead.name")).build();
        when(sceneRegistry.getProvider("test.scene")).thenReturn(provider);
        when(notifyRuleService.getEnabledRules("test.scene")).thenReturn(List.of(rule));
        when(notifyTemplateService.getNotifyTemplate(30L)).thenReturn(template);
        when(sceneRegistry.findInvalidTemplateParams("test.scene", List.of("lead.name")))
                .thenReturn(Set.of("lead.name"));

        var result = processor.processConfirmed(event);

        assertFalse(result.isSuccess());
        assertFalse(result.isRetryable());
    }
}
