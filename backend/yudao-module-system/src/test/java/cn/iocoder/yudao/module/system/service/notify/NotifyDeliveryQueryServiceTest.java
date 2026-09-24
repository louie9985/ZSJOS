package cn.iocoder.yudao.module.system.service.notify;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyDeliveryContext;
import cn.iocoder.yudao.module.system.controller.admin.notify.NotifyRuleController;
import cn.iocoder.yudao.module.system.controller.admin.notify.vo.rule.NotifyDeliveryPageReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.notify.NotifyBusinessOutboxDO;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.dal.mysql.notify.NotifyBusinessOutboxMapper;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class NotifyDeliveryQueryServiceTest {
    @Test void skippedRuleExposesStatusAndReasonWithoutPayload() {
        var row = new NotifyBusinessOutboxDO(); row.setStatus("skipped");
        row.setLastError("LEAD_SOURCE_LINK_NOT_APPLICABLE");
        row.setPayload(JsonUtils.toJsonString(new WecomOutboxPayload()));
        var result = new NotifyDeliveryQueryService().project(row);
        assertEquals("skipped", result.getStatus());
        assertEquals("LEAD_SOURCE_LINK_NOT_APPLICABLE", result.getErrorCode());
        assertEquals("wecom", result.getChannelCode());
        assertTrue(result.getRecipients().isEmpty());
    }

    @Configuration @EnableMethodSecurity static class Config {
        @Bean(name="ss") SecurityFrameworkService security() { return mock(SecurityFrameworkService.class); }
        @Bean NotifyRuleController controller() { return new NotifyRuleController(); }
        @Bean NotifyBusinessOutboxMapper mapper() { return mock(NotifyBusinessOutboxMapper.class); }
        @Bean NotifyRuleService rules() { return mock(NotifyRuleService.class); }
        @Bean NotifyDeliveryQueryService query() { return mock(NotifyDeliveryQueryService.class); }
    }
    @Test void queryPermissionIsEnforcedBeforeServiceInvocation() {
        try (var ctx = new AnnotationConfigApplicationContext(Config.class)) {
            var controller = ctx.getBean(NotifyRuleController.class);
            var query = ctx.getBean(NotifyDeliveryQueryService.class);
            var req = new NotifyDeliveryPageReqVO();
            assertThrows(AccessDeniedException.class, () -> controller.deliveries(req));
            verifyNoInteractions(query);
            when(ctx.getBean(SecurityFrameworkService.class).hasPermission("system:notify-rule:query")).thenReturn(true);
            controller.deliveries(req);
            verify(query).page(req);
        }
    }
    @Test void queryRequiresTenantAndAddsTenantPredicate() {
        var mapper = mock(NotifyBusinessOutboxMapper.class);
        var service = new NotifyDeliveryQueryService();
        ReflectionTestUtils.setField(service, "mapper", mapper);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "test"),
                NotifyBusinessOutboxDO.class);
        var req = new NotifyDeliveryPageReqVO();
        assertThrows(NullPointerException.class, () -> service.page(req));
        verifyNoInteractions(mapper);
        when(mapper.selectPage(eq(req), any(LambdaQueryWrapperX.class))).thenAnswer(call -> {
            LambdaQueryWrapperX<?> wrapper = call.getArgument(1);
            assertTrue(wrapper.getSqlSegment().contains("tenant_id ="));
            assertTrue(wrapper.getParamNameValuePairs().containsValue(TenantContextHolder.getRequiredTenantId()));
            return new PageResult<>(List.of(), 0L);
        });
        try {
            TenantContextHolder.setTenantId(9L); assertEquals(0L, service.page(req).getTotal());
            TenantContextHolder.setTenantId(8L); assertEquals(0L, service.page(req).getTotal());
        } finally { TenantContextHolder.clear(); }
    }
    @Test void malformedPayloadIsReportedWithoutReturningIt() {
        var row = new NotifyBusinessOutboxDO(); row.setPayload("malformed-private-body");
        var result = new NotifyDeliveryQueryService().project(row);
        assertEquals("NOTIFY_PAYLOAD_INVALID", result.getErrorCode());
        assertFalse(JsonUtils.toJsonString(result).contains("private-body"));
    }
    @Test void projectionNeverExposesContentPayloadTicketOrRawErrors() {
        var state = new WecomOutboxPayload(); state.setEventPayload(Map.of("private", "private-business"));
        var recipient = new WecomOutboxPayload.Recipient();
        recipient.setContext(NotifyDeliveryContext.builder().userType(3).userId(5L)
                .content("private-body").wecomClickUrl("private-ticket").build());
        recipient.setStatus("uncertain"); recipient.setErrorCode("WECOM_DELIVERY_UNCERTAIN");
        state.setRecipients(List.of(recipient));
        var row = new NotifyBusinessOutboxDO(); row.setPayload(JsonUtils.toJsonString(state));
        row.setLastError("private-error");
        String json = JsonUtils.toJsonString(new NotifyDeliveryQueryService().project(row));
        assertFalse(json.contains("private-")); assertFalse(json.contains("eventPayload"));
        assertTrue(json.contains("WECOM_DELIVERY_UNCERTAIN")); assertTrue(json.contains("wecom"));
    }
    @Test void queryRequiresConfiguredRuleQueryPermission() throws Exception {
        assertEquals("@ss.hasPermission('system:notify-rule:query')", NotifyRuleController.class
                .getMethod("deliveries", NotifyDeliveryPageReqVO.class).getAnnotation(PreAuthorize.class).value());
    }
}
