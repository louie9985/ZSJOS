package cn.iocoder.yudao.module.zsjos.controller.admin.examcalendar;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.examcalendar.vo.*;
import cn.iocoder.yudao.module.zsjos.service.examcalendar.ExamCalendarNoteService;
import org.junit.jupiter.api.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ExamCalendarNoteControllerTest {
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    @Test void returnsExplicitContractAndUsesServerTenant() throws Exception {
        TenantContextHolder.setTenantId(42L);
        var service=mock(ExamCalendarNoteService.class);
        when(service.get(eq(42L),isNull())).thenReturn(new ExamCalendarNoteRespVO().setContent("<p>说明</p>").setVersion(1L).setImages(List.of()));
        var controller=new ExamCalendarNoteController();ReflectionTestUtils.setField(controller,"service",service);
        var mvc=MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(get("/zsjos/exam-calendar/note").param("tenantId","99"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data.version").value(1))
            .andExpect(jsonPath("$.data.content").value("<p>说明</p>")).andExpect(jsonPath("$.data.tenantId").doesNotExist());
        verify(service).get(eq(42L),isNull());
        for(String body:new String[]{"{}","{\"content\":\"x\",\"version\":-1}"}) {
            mvc.perform(put("/zsjos/exam-calendar/note").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        verify(service,never()).save(any(),any(),any());
    }
    @Test void exposesConfiguredReadAndWritePermissions() throws Exception {
        assertEquals("@ss.hasPermission('zsjos:exam-calendar:query')",ExamCalendarNoteController.class.getMethod("get").getAnnotation(PreAuthorize.class).value());
        for(var method:ExamCalendarNoteController.class.getDeclaredMethods()) {
            if(method.getName().equals("save") || method.getName().equals("upload")) assertEquals("@ss.hasPermission('zsjos:exam-calendar:manage')",method.getAnnotation(PreAuthorize.class).value());
        }
    }
}
