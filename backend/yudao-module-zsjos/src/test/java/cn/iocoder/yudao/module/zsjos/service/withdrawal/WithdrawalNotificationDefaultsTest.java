package cn.iocoder.yudao.module.zsjos.service.withdrawal;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.notify.NotifyRuleApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyRecipientDTO;
import cn.iocoder.yudao.module.system.api.tenant.dto.TenantCreatedEvent;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.personnel.PartnerAccountMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.module.zsjos.enums.WithdrawalConstants.*;

class WithdrawalNotificationDefaultsTest {
    @Test void defaultsCoverEveryExistingSceneAndOnlyRegisteredRolesAndActions() {
        var provider = new WithdrawalNotifySceneProvider();
        var defaults = WithdrawalNotificationTenantInitializer.defaultRules();
        assertEquals(5, defaults.size());
        for (var scene : provider.getScenes()) {
            var rule = defaults.stream().filter(value -> value.getSceneCode().equals(scene.getCode())).findFirst().orElseThrow();
            assertTrue(scene.getAllowedActions().contains(rule.getActionType()));
            assertTrue(scene.getRecipientRoles().stream().map(role -> role.getCode()).toList().containsAll(rule.getRecipientRoles()));
            assertEquals(SCENE_FINANCE_REMINDER.equals(scene.getCode()) ? "none" : "business_detail", rule.getActionType());
        }
    }

    @Test void partnerApplicantNeverBecomesSameIdEmployee() {
        var provider = new WithdrawalNotifySceneProvider();
        var accounts = mock(PartnerAccountMapper.class);
        ReflectionTestUtils.setField(provider, "partnerAccountMapper", accounts);
        when(accounts.selectByPartnerId(80L)).thenReturn(new PartnerAccountDO().setId(12L));
        var event = NotifyBusinessEvent.builder().sceneCode(SCENE_PAID)
                .payload(Map.of("partnerId", 80L, "applicantUserId", 12L, "financeUserIds", List.of(12L))).build();
        assertEquals(Set.of(NotifyRecipientDTO.partner(12L)), provider.resolveRecipients(event, Set.of(ROLE_APPLICANT)));
        assertEquals(Set.of(NotifyRecipientDTO.admin(12L)), provider.resolveRecipients(event, Set.of(ROLE_FINANCE)));
        assertEquals(2, provider.resolveRecipients(event, Set.of(ROLE_APPLICANT, ROLE_FINANCE)).size());
    }

    @Test void missingPartnerAccountDoesNotFallBackToEmployee() {
        var provider = new WithdrawalNotifySceneProvider();
        ReflectionTestUtils.setField(provider, "partnerAccountMapper", mock(PartnerAccountMapper.class));
        var event = NotifyBusinessEvent.builder().payload(Map.of("partnerId", 80L, "applicantUserId", 12L)).build();
        assertTrue(provider.resolveRecipients(event, Set.of(ROLE_APPLICANT)).isEmpty());
    }

    @Test void newTenantInitializationRunsInEventTenantAndRestoresCaller() {
        var initializer = new WithdrawalNotificationTenantInitializer();
        var rules = mock(NotifyRuleApi.class);
        ReflectionTestUtils.setField(initializer, "notifyRuleApi", rules);
        doAnswer(call -> { assertEquals(9L, TenantContextHolder.getTenantId()); return null; })
                .when(rules).initializeDefaultRules(anyList());
        TenantContextHolder.setTenantId(1L);
        try {
            initializer.onTenantCreated(new TenantCreatedEvent(9L));
            assertEquals(1L, TenantContextHolder.getTenantId());
            verify(rules).initializeDefaultRules(WithdrawalNotificationTenantInitializer.defaultRules());
        } finally { TenantContextHolder.clear(); }
    }
}
