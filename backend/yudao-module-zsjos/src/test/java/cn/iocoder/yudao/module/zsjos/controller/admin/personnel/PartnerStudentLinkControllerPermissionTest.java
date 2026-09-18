package cn.iocoder.yudao.module.zsjos.controller.admin.personnel;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.zsjos.service.personnel.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.access.AccessDeniedException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PartnerStudentLinkControllerPermissionTest {
    @Configuration @EnableMethodSecurity static class Config {
        @Bean(name="ss") SecurityFrameworkService security() { return mock(SecurityFrameworkService.class); }
        @Bean PartnerStudentLinkController controller() { return new PartnerStudentLinkController(); }
    }
    private AnnotationConfigApplicationContext context() {
        var ctx = new AnnotationConfigApplicationContext();
        ctx.getBeanFactory().registerSingleton("service", mock(PartnerStudentLinkService.class));
        ctx.getBeanFactory().registerSingleton("manualService", mock(PartnerStudentManualLinkService.class));
        ctx.register(Config.class); ctx.refresh(); return ctx;
    }
    @Test void missingPermissionDeniesReadAndBind() {
        try (var ctx = context()) {
            var controller = ctx.getBean(PartnerStudentLinkController.class);
            assertThrows(AccessDeniedException.class, () -> controller.student(2L));
            assertThrows(AccessDeniedException.class, () -> controller.bind(3L,2L,null));
            verifyNoInteractions(ctx.getBean(PartnerStudentManualLinkService.class));
        }
    }
    @Test void allowedCallsManualBoundaryWithoutChangingRequestShape() {
        try (var ctx = context()) {
            when(ctx.getBean(SecurityFrameworkService.class).hasPermission("zsjos:partner:manage-all")).thenReturn(true);
            var controller = ctx.getBean(PartnerStudentLinkController.class);
            controller.student(2L); controller.bind(3L,2L,"checked");
            verify(ctx.getBean(PartnerStudentManualLinkService.class)).getStudent(2L);
            verify(ctx.getBean(PartnerStudentManualLinkService.class)).bind(3L,2L,"checked",null);
            verifyNoInteractions(ctx.getBean(PartnerStudentLinkService.class));
        }
    }
}
