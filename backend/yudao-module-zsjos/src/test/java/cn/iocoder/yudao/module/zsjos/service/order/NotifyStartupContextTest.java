package cn.iocoder.yudao.module.zsjos.service.order;

import cn.iocoder.yudao.module.system.api.notify.NotifyRuleApiImpl;
import cn.iocoder.yudao.module.system.service.notify.NotifyRuleServiceImpl;
import cn.iocoder.yudao.module.system.service.notify.NotifySceneRegistry;
import cn.iocoder.yudao.module.system.service.notify.NotifyTemplateServiceImpl;
import cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterService;
import cn.iocoder.yudao.module.zsjos.service.cashback.CashbackServiceImpl;
import cn.iocoder.yudao.module.zsjos.service.cashback.FinanceTraceService;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadAgingPoolServiceImpl;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadObjectPermissionService;
import jakarta.annotation.Resource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyRecipientDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.cashback.CashbackDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.cashback.CashbackMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.beans.Introspector;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static cn.iocoder.yudao.module.zsjos.enums.SalesOrderNotifySceneConstants.SUBMITTER_EFFECTIVE;

class NotifyStartupContextTest {
    private static final Class<?>[] SERVICES = {
            NotifySceneRegistry.class, SalesOrderNotifySceneProvider.class, CashbackServiceImpl.class,
            AdvancedFilterService.class, FinanceTraceService.class, LeadObjectPermissionService.class,
            SalesOrderObjectPermissionService.class, LeadAgingPoolServiceImpl.class,
            NotifyRuleApiImpl.class, NotifyRuleServiceImpl.class, NotifyTemplateServiceImpl.class
    };

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    @EnableMethodSecurity
    static class Proxies { }

    @ParameterizedTest
    @ValueSource(ints = {0, 4, 7})
    void realNotificationAndFinanceGraphStartsWithSecurityAndTransactionProxies(int firstService) {
        try (var context = new AnnotationConfigApplicationContext()) {
            // Match the application: circular field references are allowed, raw proxy targets are not.
            context.setAllowCircularReferences(true);
            context.register(Proxies.class);
            context.registerBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class));
            Set<Class<?>> leaves = new LinkedHashSet<>();
            for (Class<?> service : SERVICES) {
                for (var field : service.getDeclaredFields()) {
                    if (field.isAnnotationPresent(Resource.class)
                            && Arrays.stream(SERVICES).noneMatch(field.getType()::isAssignableFrom)) {
                        leaves.add(field.getType());
                    }
                }
            }
            leaves.forEach(type -> registerMock(context, type));
            context.register(SERVICES[firstService]);
            context.register(SERVICES);
            context.refresh();
            assertThat(context.getBean(NotifySceneRegistry.class).getScenes()).hasSize(8);
            assertThat(AopUtils.isAopProxy(context.getBean(FinanceTraceService.class))).isTrue();
            assertThat(AopUtils.isAopProxy(context.getBean(LeadAgingPoolServiceImpl.class))).isTrue();
            verifyCashbackResolution(context);
        }
    }

    private static void verifyCashbackResolution(AnnotationConfigApplicationContext context) {
        SalesOrderDO order = new SalesOrderDO();
        order.setId(7L);
        order.setOrderNo("ORDER-CONTEXT-TEST");
        when(context.getBean(SalesOrderMapper.class).selectById(7L)).thenReturn(order);
        CashbackDO cashback = new CashbackDO();
        cashback.setAmount(new BigDecimal("12.34"));
        CashbackMapper mapper = context.getBean(CashbackMapper.class);
        when(mapper.selectList(org.mockito.ArgumentMatchers.<Wrapper<CashbackDO>>any()))
                .thenReturn(List.of(cashback));
        var provider = context.getBean(NotifySceneRegistry.class).getProvider(SUBMITTER_EFFECTIVE);
        for (String identity : List.of("leadSubmitterUserId", "partnerId")) {
            var event = NotifyBusinessEvent.builder().sceneCode(SUBMITTER_EFFECTIVE).bizId(7L)
                    .payload(Map.<String, Object>of(identity, 9L)).build();
            assertThat(provider.resolveVariables(event, NotifyRecipientDTO.admin(9L))
                    .get("order.cashbackTotal")).isEqualTo(new BigDecimal("12.34"));
        }
        verify(mapper, times(2)).selectList(org.mockito.ArgumentMatchers.<Wrapper<CashbackDO>>any());
    }

    private static <T> void registerMock(AnnotationConfigApplicationContext context, Class<T> type) {
        context.getBeanFactory().registerSingleton(Introspector.decapitalize(type.getSimpleName()), mock(type));
    }
}
