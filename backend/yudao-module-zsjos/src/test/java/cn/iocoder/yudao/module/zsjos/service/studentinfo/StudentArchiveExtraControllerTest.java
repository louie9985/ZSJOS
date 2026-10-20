package cn.iocoder.yudao.module.zsjos.service.studentinfo;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.StudentArchiveExtraController;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentArchiveExtraVO.Save;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentArchiveExtraVO.VisibilitySave;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class StudentArchiveExtraControllerTest {
    @Configuration @EnableMethodSecurity static class Config {
        @Bean(name="ss") SecurityFrameworkService security() { return mock(SecurityFrameworkService.class); }
        @Bean StudentArchiveExtraController controller() { return new StudentArchiveExtraController(); }
    }
    @Test void configuredReadUpdateAndSensitivePermissionsAreCumulative() throws Exception {
        try(var context=new AnnotationConfigApplicationContext()) {
            var service=mock(StudentArchiveExtraService.class);
            context.getBeanFactory().registerSingleton("extraService",service);context.register(Config.class);context.refresh();
            var controller=context.getBean(StudentArchiveExtraController.class);var ss=context.getBean(SecurityFrameworkService.class);
            assertThrows(AccessDeniedException.class,()->controller.detail(5L));
            when(ss.hasPermission("zsjos:student-archive:read")).thenReturn(true);
            controller.detail(5L);verify(service).detail(5L);
            assertThrows(AccessDeniedException.class,()->controller.save(5L,new Save()));
            when(ss.hasPermission("zsjos:student-archive:update")).thenReturn(true);
            controller.save(5L,new Save());verify(service).save(eq(5L),any(Save.class));
            var file=new MockMultipartFile("file","fixture.pdf","application/pdf","%PDF-1.7".getBytes());
            assertThrows(AccessDeniedException.class,()->controller.upload(5L,"theory",file));
            assertThrows(AccessDeniedException.class,()->controller.download(5L,7L,new MockHttpServletResponse()));
            when(ss.hasPermission("zsjos:student-info-form:sensitive-read")).thenReturn(true);
            controller.upload(5L,"theory",file);verify(service).upload(5L,"theory",file);
            var response=new MockHttpServletResponse();controller.download(5L,7L,response);
            assertEquals("no-store",response.getHeader("Cache-Control"));assertEquals("nosniff",response.getHeader("X-Content-Type-Options"));
            when(ss.hasPermission("zsjos:student-archive:read")).thenReturn(false);
            assertThrows(AccessDeniedException.class,()->controller.save(5L,new Save()));
            assertThrows(AccessDeniedException.class,()->controller.download(5L,7L,new MockHttpServletResponse()));
            var visibility = new VisibilitySave(); visibility.setRevision(0); visibility.setVisibility(new java.util.LinkedHashMap<>());
            assertThrows(AccessDeniedException.class, () -> controller.saveVisibility(visibility));
            when(ss.hasPermission("zsjos:exam-calendar:manage")).thenReturn(true);
            controller.saveVisibility(visibility); verify(service).saveVisibility(any(VisibilitySave.class));
        }
    }
    @Test void apiBindsCompositeValuesAndRejectsInvalidCommands() throws Exception {
        var service=mock(StudentArchiveExtraService.class);var controller=new StudentArchiveExtraController();
        ReflectionTestUtils.setField(controller,"service",service);
        var mvc=MockMvcBuilders.standaloneSetup(controller).build();
        String base="/zsjos/student/service/5/archive/extra";
        mvc.perform(post(base).contentType("application/json").content("{\"revision\":0,\"idempotencyKey\":\"fixture\",\"reason\":\"fixture\",\"changes\":{}}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
        when(service.save(eq(5L),any())).thenReturn(1);
        mvc.perform(post(base).contentType("application/json").content("{\"revision\":0,\"idempotencyKey\":\"fixture\",\"reason\":\"fixture\",\"changes\":{\"theory\":{\"text\":\"85\",\"links\":[\"https://example.com\"],\"attachmentIds\":[7]}}}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data").value(1));
        var capture=org.mockito.ArgumentCaptor.forClass(Save.class);verify(service).save(eq(5L),capture.capture());
        var value=capture.getValue().getChanges().get("theory");assertEquals("85",value.getText());assertEquals(java.util.List.of(7L),value.getAttachmentIds());
    }
}
