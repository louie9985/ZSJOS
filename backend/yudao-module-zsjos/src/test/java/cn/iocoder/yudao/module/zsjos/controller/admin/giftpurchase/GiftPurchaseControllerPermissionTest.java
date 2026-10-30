package cn.iocoder.yudao.module.zsjos.controller.admin.giftpurchase;

import cn.iocoder.yudao.module.zsjos.controller.admin.giftpurchase.vo.*;
import cn.iocoder.yudao.module.zsjos.service.giftpurchase.GiftPurchaseService;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GiftPurchaseControllerPermissionTest {
    public static class Gate {
        Set<String> permissions = new HashSet<>();
        public boolean hasPermission(String permission) { return permissions.contains(permission); }
        public boolean hasAnyPermissions(String... values) { return Arrays.stream(values).anyMatch(permissions::contains); }
    }
    @Configuration @EnableMethodSecurity
    static class Config {
        @Bean cn.iocoder.yudao.module.zsjos.dal.mysql.giftpurchase.GiftPurchaseMapper mapper() { return mock(cn.iocoder.yudao.module.zsjos.dal.mysql.giftpurchase.GiftPurchaseMapper.class); }
        @Bean cn.iocoder.yudao.module.zsjos.dal.mysql.giftpurchase.GiftPurchaseOperationMapper operationMapper() { return mock(cn.iocoder.yudao.module.zsjos.dal.mysql.giftpurchase.GiftPurchaseOperationMapper.class); }
        @Bean cn.iocoder.yudao.module.zsjos.service.gift.GiftConfigService giftConfigService() { return mock(cn.iocoder.yudao.module.zsjos.service.gift.GiftConfigService.class); }
        @Bean cn.iocoder.yudao.module.system.api.user.AdminUserApi userApi() { return mock(cn.iocoder.yudao.module.system.api.user.AdminUserApi.class); }
        @Bean cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper orderMapper() { return mock(cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper.class); }
        @Bean cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderApprovalRoundMapper roundMapper() { return mock(cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderApprovalRoundMapper.class); }
        @Bean cn.iocoder.yudao.module.zsjos.service.giftpurchase.GiftPurchaseBatchItemService batchItemService() { return mock(cn.iocoder.yudao.module.zsjos.service.giftpurchase.GiftPurchaseBatchItemService.class); }
        @Bean(name="ss") Gate gate() { return new Gate(); }
        @Bean GiftPurchaseService service() { return mock(GiftPurchaseService.class); }
        @Bean GiftPurchaseController controller(GiftPurchaseService service) {
            var controller = new GiftPurchaseController();
            org.springframework.test.util.ReflectionTestUtils.setField(controller,"service", service); return controller;
        }
    }
    @Test void exportAndBatchRequireTheirOwnPermissions() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("fixture", "", "ROLE_FIXTURE"));
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var controller=context.getBean(GiftPurchaseController.class); var gate=context.getBean(Gate.class);
            var service=context.getBean(GiftPurchaseService.class);
            var batch=new GiftPurchaseBatchReqVO(); batch.setIds(List.of(1L));
            var response=new org.springframework.mock.web.MockHttpServletResponse();
            for(String permission:List.of("query","detail","update")) {
                gate.permissions.clear(); gate.permissions.add("zsjos:gift-purchase:"+permission);
                assertThrows(AccessDeniedException.class,()->controller.batchPurchase(batch));
                assertThrows(AccessDeniedException.class,()->controller.exportExcel(new GiftPurchasePageReqVO(),response));
            }
            verifyNoInteractions(service);
            gate.permissions.clear();gate.permissions.add("zsjos:gift-purchase:export");
            when(service.exportExcel(any())).thenReturn(new byte[]{1,2});
            controller.exportExcel(new GiftPurchasePageReqVO(),response);
            assertArrayEquals(new byte[]{1,2},response.getContentAsByteArray());
            assertThrows(AccessDeniedException.class,()->controller.batchPurchase(batch));
            gate.permissions.clear();gate.permissions.add("zsjos:gift-purchase:batch-purchase");
            assertDoesNotThrow(()->controller.batchPurchase(batch));
            assertThrows(AccessDeniedException.class,()->controller.exportExcel(new GiftPurchasePageReqVO(),response));
        } finally { SecurityContextHolder.clearContext(); }
    }
    @Test void realMethodSecurityEnforcesSeparateCrudPermissionsAndUpdateAlsoAllowsLoadingEditor() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("fixture", "", "ROLE_FIXTURE"));
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var controller = context.getBean(GiftPurchaseController.class); var gate = context.getBean(Gate.class);
            var service = context.getBean(GiftPurchaseService.class);
            var req = new GiftPurchaseSaveReqVO(); req.setId(1L);
            List<Runnable> calls = List.of(() -> controller.page(new GiftPurchasePageReqVO()), () -> controller.get(1L),
                    () -> controller.operations(1L), () -> controller.create(req), () -> controller.update(req),
                    () -> controller.purchase(1L), () -> controller.unpurchase(1L), () -> controller.delete(1L));
            calls.forEach(call -> assertThrows(AccessDeniedException.class, call::run)); verifyNoInteractions(service);
            gate.permissions.add("zsjos:gift-purchase:query"); assertDoesNotThrow(calls.get(0)::run);
            gate.permissions.clear(); gate.permissions.add("zsjos:gift-purchase:update");
            assertDoesNotThrow(() -> controller.get(1L)); assertDoesNotThrow(() -> controller.update(req));
            assertDoesNotThrow(() -> controller.purchase(1L)); assertDoesNotThrow(() -> controller.unpurchase(1L));
            assertThrows(AccessDeniedException.class, () -> controller.delete(1L));
            assertThrows(AccessDeniedException.class, () -> controller.operations(1L));
            gate.permissions.clear(); gate.permissions.add("zsjos:gift-purchase:detail");
            assertDoesNotThrow(() -> controller.operations(1L));
            gate.permissions.clear(); gate.permissions.add("zsjos:gift-purchase:create"); assertDoesNotThrow(() -> controller.create(req));
            gate.permissions.clear(); gate.permissions.add("zsjos:gift-purchase:delete"); assertDoesNotThrow(() -> controller.delete(1L));
        } finally { SecurityContextHolder.clearContext(); }
    }
}
