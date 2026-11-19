package cn.iocoder.yudao.module.zsjos.controller.admin.giftpurchase;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.module.zsjos.service.studentinfo.TextbookAddressService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TextbookAddressPermissionTest {
    @Configuration @EnableMethodSecurity
    @Import(TextbookAddressController.class)
    static class Config {
        @Bean(name = "ss") GiftPurchaseControllerPermissionTest.Gate gate() {
            return new GiftPurchaseControllerPermissionTest.Gate();
        }

    }
    @Test void allThreePermissionsRequiredBeforeReadingAddresses() {
        var required = List.of("zsjos:gift-purchase:query", "zsjos:sales-order:query-management",
                "zsjos:student-info-form:sensitive-read");
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("fixture", "", "ROLE_FIXTURE"));
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getBeanFactory().registerSingleton("service", mock(TextbookAddressService.class));
            context.register(Config.class);
            context.refresh();
            var gate = context.getBean(GiftPurchaseControllerPermissionTest.Gate.class);
            var controller = context.getBean(TextbookAddressController.class);
            var response = new MockHttpServletResponse();
            for (var missing : required) {
                gate.permissions.addAll(required);
                gate.permissions.remove(missing);
                assertThrows(AccessDeniedException.class, () -> controller.page(new PageParam(), "", response));
            }
            verifyNoInteractions(context.getBean(TextbookAddressService.class));
            gate.permissions.addAll(required);
            controller.page(new PageParam(), "", response);
            assertEquals("no-store", response.getHeader("Cache-Control"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
