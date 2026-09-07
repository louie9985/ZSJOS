package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.LeadSubmitterFeedbackController;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadSubmitterFeedbackReqVO;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.access.AccessDeniedException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class LeadSubmitterFeedbackFeaturePermissionTest {
    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean(name = "ss") SecurityFrameworkService security() { return mock(SecurityFrameworkService.class); }
        @Bean LeadSubmitterFeedbackController controller() { return new LeadSubmitterFeedbackController(); }
    }
    private AnnotationConfigApplicationContext context() {
        var context = new AnnotationConfigApplicationContext();
        context.getBeanFactory().registerSingleton("service", mock(LeadSubmitterFeedbackService.class));
        context.register(Config.class);
        context.refresh();
        return context;
    }
    @Test void deniesMissingReadAndCreatePermissionsBeforeCallingService() {
        try (var context = context()) {
            var controller = context.getBean(LeadSubmitterFeedbackController.class);
            assertThrows(AccessDeniedException.class, () -> controller.page(1L, new PageParam()));
            assertThrows(AccessDeniedException.class, () -> controller.create(1L, new LeadSubmitterFeedbackReqVO()));
            verifyNoInteractions(context.getBean(LeadSubmitterFeedbackService.class));
        }
    }
    @Test void configuredCreatePermissionAllowsControllerToReachObjectCheckedService() {
        try (var context = context()) {
            var security = context.getBean(SecurityFrameworkService.class);
            when(security.hasPermission("zsjos:lead:submitter-feedback:create")).thenReturn(true);
            var service = context.getBean(LeadSubmitterFeedbackService.class);
            when(service.create(eq(1L), isNull(), any())).thenReturn(7L);
            assertEquals(7L, context.getBean(LeadSubmitterFeedbackController.class)
                    .create(1L, new LeadSubmitterFeedbackReqVO()).getData());
        }
    }
}
