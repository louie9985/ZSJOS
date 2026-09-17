package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.LeadSubmissionController;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadCreateReqVO;
import cn.iocoder.yudao.module.zsjos.service.product.ZsjosProductSkuService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.access.AccessDeniedException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class LeadEducationSubmissionPermissionTest {
    @Configuration @EnableMethodSecurity
    static class Config {
        @Bean(name = "ss") SecurityFrameworkService security() { return mock(SecurityFrameworkService.class); }
        @Bean LeadSubmissionController controller() { return new LeadSubmissionController(); }
    }
    private AnnotationConfigApplicationContext context() {
        var context = new AnnotationConfigApplicationContext();
        context.getBeanFactory().registerSingleton("submissionService", mock(LeadSubmissionService.class));
        context.getBeanFactory().registerSingleton("attachmentService", mock(LeadAttachmentService.class));
        context.getBeanFactory().registerSingleton("productService", mock(LeadProductService.class));
        context.getBeanFactory().registerSingleton("skuService", mock(ZsjosProductSkuService.class));
        context.register(Config.class); context.refresh(); return context;
    }
    @Test void missingEducationPermissionDeniesBeforeService() {
        try (var context = context()) {
            when(context.getBean(SecurityFrameworkService.class).hasPermission("zsjos:lead:self-sourced:create")).thenReturn(true);
            assertThrows(AccessDeniedException.class, () -> context.getBean(LeadSubmissionController.class)
                    .createEducationSelfSourced(new LeadCreateReqVO()));
            verifyNoInteractions(context.getBean(LeadSubmissionService.class));
        }
    }
    @Test void educationPermissionEnablesOnlyEducationEntry() {
        try (var context = context()) {
            var security = context.getBean(SecurityFrameworkService.class);
            when(security.hasPermission("zsjos:lead:education-self-sourced:create")).thenReturn(true);
            var controller = context.getBean(LeadSubmissionController.class);
            controller.createEducationSelfSourced(new LeadCreateReqVO());
            verify(context.getBean(LeadSubmissionService.class)).createEducationSelfSourced(any(), isNull());
            assertThrows(AccessDeniedException.class, () -> controller.createSelfSourced(new LeadCreateReqVO()));
        }
    }
}
