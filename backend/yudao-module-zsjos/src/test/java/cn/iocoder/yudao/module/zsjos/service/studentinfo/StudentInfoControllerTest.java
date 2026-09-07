package cn.iocoder.yudao.module.zsjos.service.studentinfo;

import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.StudentInfoFormController;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.StudentInfoExceptionHandler;
import cn.iocoder.yudao.module.zsjos.controller.pub.studentinfo.PublicStudentInfoFormController;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StudentInfoControllerTest {
    @Configuration @EnableMethodSecurity
    static class Config {
        @Bean(name="ss") SecurityFrameworkService security() { return mock(SecurityFrameworkService.class); }
        @Bean StudentInfoFormController controller() { return new StudentInfoFormController(); }
    }
    @Test void sensitiveReadRequiresBothPermissionsAndExportIsIndependent() throws Exception {
        try (var context=new AnnotationConfigApplicationContext()) {
            var service=mock(StudentInfoFormService.class);
            context.getBeanFactory().registerSingleton("service",service); context.register(Config.class); context.refresh();
            var controller=context.getBean(StudentInfoFormController.class);
            var ss=context.getBean(SecurityFrameworkService.class);
            assertThrows(AccessDeniedException.class, () -> controller.generate(1L));
            when(ss.hasPermission("zsjos:student-info-form:read")).thenReturn(true);
            controller.detail(1L); verify(service).detail(1L);
            assertThrows(AccessDeniedException.class, () -> controller.sensitive(1L));
            assertThrows(AccessDeniedException.class, () -> controller.export(1L,new org.springframework.mock.web.MockHttpServletResponse()));
            when(ss.hasPermission("zsjos:student-info-form:sensitive-read")).thenReturn(true);
            controller.sensitive(1L); verify(service).sensitiveDetail(1L);
            when(ss.hasPermission("zsjos:student-info-form:read")).thenReturn(false);
            assertThrows(AccessDeniedException.class, () -> controller.sensitive(1L));
        }
    }
    @Test void publicValidationAndUnexpectedErrorsNeverReturnPayloads() throws Exception {
        var controller=new PublicStudentInfoFormController(); var service=mock(StudentInfoFormService.class);
        ReflectionTestUtils.setField(controller,"service",service);
        var mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new StudentInfoExceptionHandler()).build();
        mvc.perform(post("/zsjos/student-info-form/submit").header("X-Student-Info-Token","a".repeat(43))
                .contentType("application/json").content("{}"))
                .andExpect(jsonPath("$.code").value(400)).andExpect(header().string("Cache-Control","no-store"));
        verifyNoInteractions(service);
        when(service.publicDetail(anyString())).thenThrow(new IllegalStateException("private fixture must not escape"));
        mvc.perform(get("/zsjos/student-info-form/detail").header("X-Student-Info-Token","a".repeat(43)))
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("信息收集服务暂不可用，请稍后重试"));
    }
}
