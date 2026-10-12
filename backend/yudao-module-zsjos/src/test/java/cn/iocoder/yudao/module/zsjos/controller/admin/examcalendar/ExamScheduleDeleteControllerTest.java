package cn.iocoder.yudao.module.zsjos.controller.admin.examcalendar;

import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.zsjos.service.examcalendar.ExamScheduleService;
import cn.iocoder.yudao.module.zsjos.service.calendar.CalendarMaintenanceNotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ExamScheduleDeleteControllerTest {
    @Configuration @EnableMethodSecurity
    static class Config {
        @Bean(name = "ss") SecurityFrameworkService security() { return mock(SecurityFrameworkService.class); }
        @Bean ExamScheduleController controller() { return new ExamScheduleController(); }
    }

    @Test void deleteHttpContractRequiresConfiguredManagePermission() throws Exception {
        try (var context = new AnnotationConfigApplicationContext()) {
            var service = mock(ExamScheduleService.class);
            context.getBeanFactory().registerSingleton("service", service);
            context.getBeanFactory().registerSingleton("notification", mock(CalendarMaintenanceNotificationService.class));
            context.register(Config.class);
            context.refresh();
            var controller = context.getBean(ExamScheduleController.class);
            var security = context.getBean(SecurityFrameworkService.class);
            when(security.hasPermission("zsjos:exam-calendar:query")).thenReturn(true);
            assertThrows(AccessDeniedException.class, () -> controller.deleteDraft(9L));
            verifyNoInteractions(service);
            when(security.hasPermission("zsjos:exam-calendar:manage")).thenReturn(true);
            var mvc = MockMvcBuilders.standaloneSetup(controller).build();
            mvc.perform(delete("/zsjos/exam-calendar/delete/9"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").value(true));
            verify(service).deleteDraft(eq(9L), isNull());
            clearInvocations(service);
            when(security.hasPermission("zsjos:exam-calendar:manage")).thenReturn(false);
            assertThrows(AccessDeniedException.class, () -> controller.deleteDraft(9L));
            verifyNoInteractions(service);
        }
    }
}
