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
    @Test void pageAndEditPermissionsAreIndependent() {

        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("fixture", "", "ROLE_FIXTURE"));
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getBeanFactory().registerSingleton("service", mock(TextbookAddressService.class));
            context.register(Config.class);
            context.refresh();
            var gate = context.getBean(GiftPurchaseControllerPermissionTest.Gate.class);
            var controller = context.getBean(TextbookAddressController.class);
            var response = new MockHttpServletResponse();
            gate.permissions.add("zsjos:textbook-shipping:update");
            assertThrows(AccessDeniedException.class, () -> controller.page(new PageParam(), "", response));
            assertThrows(AccessDeniedException.class, () -> controller.save(11L, 1L, null));
            gate.permissions.clear();
            gate.permissions.add("zsjos:textbook-shipping:query");
            controller.page(new PageParam(), "", response);
            gate.permissions.add("zsjos:textbook-shipping:view-all");
            assertThrows(AccessDeniedException.class, () -> controller.save(11L, 1L, null));
            gate.permissions.remove("zsjos:textbook-shipping:view-all");
            gate.permissions.add("zsjos:textbook-shipping:update");
            controller.save(11L, 1L, null);
            assertEquals("no-store", response.getHeader("Cache-Control"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
