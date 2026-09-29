package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.MediaStudentController;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.StudentServicePeriodUpdateReqVO;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MediaStudentServicePeriodControllerTest {
    @Configuration @EnableMethodSecurity
    static class Config {
        @Bean(name = "ss") SecurityFrameworkService security() { return mock(SecurityFrameworkService.class); }
        @Bean MediaStudentController controller() { return new MediaStudentController(); }
    }

    private AnnotationConfigApplicationContext context() {
        var context = new AnnotationConfigApplicationContext();
        context.getBeanFactory().registerSingleton("students", mock(MyStudentService.class));
        context.getBeanFactory().registerSingleton("media", mock(MediaStudentService.class));
        context.getBeanFactory().registerSingleton("partner", mock(MediaStudentPartnerContextService.class));
        context.getBeanFactory().registerSingleton("period", mock(MediaStudentServicePeriodService.class));
        context.register(Config.class); context.refresh();
        return context;
    }

    @Test void deniesUnconfiguredOperationBeforeService() {
        try (var context = context()) {
            var request = new StudentServicePeriodUpdateReqVO().setInServicePeriod(false);
            assertThrows(AccessDeniedException.class,
                    () -> context.getBean(MediaStudentController.class).updateServicePeriod(20L, request));
            verifyNoInteractions(context.getBean(MediaStudentServicePeriodService.class));
        }
    }

    @Test void configuredOperationReachesObjectCheckedServiceAndReturnsFalse() {
        try (var context = context()) {
            when(context.getBean(SecurityFrameworkService.class).hasPermission(MediaStudentServicePeriodService.PERMISSION))
                    .thenReturn(true);
            var request = new StudentServicePeriodUpdateReqVO().setInServicePeriod(false);
            assertFalse(context.getBean(MediaStudentController.class).updateServicePeriod(20L, request).getData());
            verify(context.getBean(MediaStudentServicePeriodService.class)).update(null, 20L, false);
        }
    }
}
