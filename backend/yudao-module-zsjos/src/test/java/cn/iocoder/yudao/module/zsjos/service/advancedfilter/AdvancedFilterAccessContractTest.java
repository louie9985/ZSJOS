package cn.iocoder.yudao.module.zsjos.service.advancedfilter;

import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.AdvancedFilterController;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterCatalogRespVO;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.authorization.method.PreAuthorizeAuthorizationManager;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.PostMapping;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdvancedFilterAccessContractTest {
    public static class Permissions {
        String granted = "";
        public boolean hasPermission(String value) { return granted.equals(value); }
        public boolean hasAnyPermissions(String... values) { return List.of(values).contains(granted); }
    }
    private final Permissions permissions = new Permissions();
    private final StaticApplicationContext context = new StaticApplicationContext();
    private AuthorizationManagerBeforeMethodInterceptor interceptor;

    @BeforeEach void setup() {
        context.getBeanFactory().registerSingleton("ss", permissions);
        context.refresh();
        var handler = new DefaultMethodSecurityExpressionHandler(); handler.setApplicationContext(context);
        var manager = new PreAuthorizeAuthorizationManager(); manager.setExpressionHandler(handler);
        interceptor = AuthorizationManagerBeforeMethodInterceptor.preAuthorize(manager);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("contract-user", "unused", List.of()));
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); context.close(); }
    private Object secured(Object target) {
        var factory = new ProxyFactory(target); factory.setProxyTargetClass(true); factory.addAdvisor(interceptor);
        return factory.getProxy();
    }

    @Test void deniedCatalogNeverLoadsFieldsOrOptionsAndAllowedSceneDoes() {
        var target = new AdvancedFilterController();
        var service = mock(AdvancedFilterService.class);
        var users = mock(AdvancedFilterVisibleUserService.class);
        var products = mock(AdvancedFilterProductOptions.class);
        ReflectionTestUtils.setField(target, "service", service);
        ReflectionTestUtils.setField(target, "visibleUserService", users);
        ReflectionTestUtils.setField(target, "productOptions", products);
        var controller = (AdvancedFilterController) secured(target);
        for (String scene : AdvancedFilterFieldCatalog.SCENES) {
            assertThrows(AccessDeniedException.class, () -> controller.catalog(scene), scene);
        }
        verifyNoInteractions(service, users, products);
        permissions.granted = "zsjos:withdrawal:my-query";
        assertThrows(AccessDeniedException.class, () -> controller.catalog("order"));
        when(users.resolve(eq("withdrawal"), nullable(Long.class)))
                .thenReturn(new AdvancedFilterVisibleUserService.Resolution(true, List.of()));
        var catalog = new AdvancedFilterCatalogRespVO(List.of(), List.of());
        when(service.catalog("withdrawal", List.of())).thenReturn(catalog);
        when(products.resolve(catalog)).thenReturn(catalog);
        assertSame(catalog, controller.catalog("withdrawal").getData());
        verify(service).catalog("withdrawal", List.of());
    }

    @Test void everySearchEntryRejectsUnauthorizedUseBeforeExecutingBusinessCode() throws Exception {
        int checked = 0;
        for (String name : List.of("lead.LeadManagementController", "lead.LeadDispatchController",
                "lead.LeadAgingPoolController", "lead.LeadQualificationController", "lead.LeadAppealController",
                "lead.LeadDuplicateReviewController", "lead.SubordinateSalesController", "order.SalesOrderController",
                "registration.RegistrationController", "registration.MyStudentController",
                "cashback.CashbackController", "withdrawal.WithdrawalController")) {
            Class<?> type = Class.forName("cn.iocoder.yudao.module.zsjos.controller.admin." + name);
            Object proxy = secured(type.getDeclaredConstructor().newInstance());
            int endpoints = 0;
            for (var method : type.getMethods()) {
                var mapping = method.getAnnotation(PostMapping.class);
                if (mapping == null || Arrays.stream(mapping.value()).noneMatch(path -> path.contains("search-page"))) continue;
                assertNotNull(method.getAnnotation(PreAuthorize.class), name + "." + method.getName());
                var thrown = assertThrows(InvocationTargetException.class,
                        () -> method.invoke(proxy, new Object[method.getParameterCount()]));
                assertInstanceOf(AccessDeniedException.class, thrown.getCause(), name + "." + method.getName());
                endpoints++; checked++;
            }
            assertTrue(endpoints > 0, "Removed/renamed search entry requires contract update: " + name);
        }
        assertTrue(checked >= 12);
    }
}
