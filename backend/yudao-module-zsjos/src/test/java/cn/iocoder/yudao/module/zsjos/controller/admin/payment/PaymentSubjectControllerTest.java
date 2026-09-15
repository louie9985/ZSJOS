package cn.iocoder.yudao.module.zsjos.controller.admin.payment;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.zsjos.controller.admin.payment.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.payment.PaymentSubjectDO;
import cn.iocoder.yudao.module.zsjos.service.payment.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PaymentSubjectControllerTest {
    @Test
    void pageAndSimpleListHaveRegisteredRoutesAndNeverExposeKeys() throws Exception {
        var service = mock(PaymentSubjectService.class);
        var subject = new PaymentSubjectDO();
        subject.setId(1L); subject.setSubjectName("fixture"); subject.setStatus(0);
        subject.setMerchantPrivateKey("test-private-key"); subject.setPlatformPublicKey("test-public-key");
        when(service.getPaymentSubjectPage(any())).thenReturn(new PageResult<>(List.of(subject), 11L));
        when(service.getPaymentSubjectList(null)).thenReturn(List.of(subject));
        var controller = new PaymentSubjectController();
        ReflectionTestUtils.setField(controller, "paymentSubjectService", service);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(get("/zsjos/payment-subject/page").param("pageNo", "2").param("pageSize", "10")
                        .param("subjectName", "fixture").param("status", "0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(11))
                .andExpect(jsonPath("$.data.list[0].status").value(0))
                .andExpect(jsonPath("$.data.list[0].merchantPrivateKey").doesNotExist())
                .andExpect(jsonPath("$.data.list[0].platformPublicKey").doesNotExist());
        verify(service).getPaymentSubjectPage(argThat(req -> req.getPageNo() == 2
                && req.getPageSize() == 10 && req.getStatus() == 0 && "fixture".equals(req.getSubjectName())));
        mvc.perform(get("/zsjos/payment-subject/simple-list"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].merchantPrivateKey").doesNotExist());
        when(service.getPaymentSubjectPage(any())).thenReturn(new PageResult<>(List.of(), 0L));
        mvc.perform(get("/zsjos/payment-subject/page"))
                .andExpect(jsonPath("$.data.list").isEmpty()).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(put("/zsjos/payment-subject/update-status").param("id", "1").param("status", "0"))
                .andExpect(status().isOk());
        verify(service).updatePaymentSubjectStatus(1L, 0);
    }

    @Test
    void productPageAndBatchConfigureUseThePublicRequestContract() throws Exception {
        var service = mock(ProductPaymentSubjectService.class);
        when(service.getProductPaymentSubjectPage(any())).thenReturn(new PageResult<>(List.of(), 0L));
        var controller = new ProductPaymentSubjectController();
        ReflectionTestUtils.setField(controller, "productPaymentSubjectService", service);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(get("/zsjos/product-payment-subject/page").param("productName", "A").param("paymentSubjectId", "9"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        verify(service).getProductPaymentSubjectPage(argThat(req -> req.getPaymentSubjectId() == 9L && "A".equals(req.getProductName())));
        mvc.perform(post("/zsjos/product-payment-subject/batch-configure").contentType("application/json")
                        .content("{\"productIds\":[1,2],\"paymentSubjectId\":9}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(true));
        verify(service).batchConfigureProductPaymentSubject(List.of(1L, 2L), 9L);
        mvc.perform(post("/zsjos/product-payment-subject/batch-configure").contentType("application/json")
                        .content("{\"productIds\":[1],\"subjectId\":9}"))
                .andExpect(status().isBadRequest());
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean(name = "ss") SecurityFrameworkService security() { return mock(SecurityFrameworkService.class); }
        @Bean PaymentSubjectService subjects() { return mock(PaymentSubjectService.class); }
        @Bean ProductPaymentSubjectService products() { return mock(ProductPaymentSubjectService.class); }
        @Bean PaymentSubjectController subjectController() { return new PaymentSubjectController(); }
        @Bean ProductPaymentSubjectController productController() { return new ProductPaymentSubjectController(); }
    }

    @Test
    void newReadEndpointsEnforceConfiguredPermissions() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var controller = context.getBean(PaymentSubjectController.class);
            var product = context.getBean(ProductPaymentSubjectController.class);
            var ss = context.getBean(SecurityFrameworkService.class);
            var service = context.getBean(PaymentSubjectService.class);
            var products = context.getBean(ProductPaymentSubjectService.class);
            assertThrows(AccessDeniedException.class, controller::getPaymentSubjectSimpleList);
            assertThrows(AccessDeniedException.class, () -> controller.getPaymentSubjectPage(new PaymentSubjectPageReqVO()));
            assertThrows(AccessDeniedException.class, () -> product.getProductPaymentSubjectPage(new ProductPaymentSubjectPageReqVO()));
            verifyNoInteractions(service, products);
            when(ss.hasAnyPermissions("zsjos:payment-subject:query", "zsjos:product-payment-subject:query", "zsjos:product-payment-subject:configure")).thenReturn(true);
            when(service.getPaymentSubjectList(null)).thenReturn(List.of());
            assertTrue(controller.getPaymentSubjectSimpleList().getData().isEmpty());
            when(ss.hasPermission("zsjos:payment-subject:query")).thenReturn(true);
            when(service.getPaymentSubjectPage(any())).thenReturn(new PageResult<>(List.of(), 0L));
            assertEquals(0L, controller.getPaymentSubjectPage(new PaymentSubjectPageReqVO()).getData().getTotal());
            when(ss.hasPermission("zsjos:product-payment-subject:query")).thenReturn(true);
            when(products.getProductPaymentSubjectPage(any())).thenReturn(new PageResult<>(List.of(), 0L));
            assertEquals(0L, product.getProductPaymentSubjectPage(new ProductPaymentSubjectPageReqVO()).getData().getTotal());
        }
    }
}
