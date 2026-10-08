package cn.iocoder.yudao.module.zsjos.controller.admin.examcalendar;

import cn.iocoder.yudao.module.zsjos.controller.admin.examcalendar.vo.ExamScheduleAttachmentRespVO;
import cn.iocoder.yudao.module.zsjos.service.examcalendar.ExamScheduleAttachmentService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ExamScheduleAttachmentControllerTest {
    @Test void exposesMultipartUploadAndBoundReadContract() throws Exception {
        var service=mock(ExamScheduleAttachmentService.class);
        var file=new ExamScheduleAttachmentRespVO().setFileId(11L).setName("官方通知.pdf").setType("application/pdf").setSize(12L).setUrl("https://example.test/signed");
        when(service.upload(any(),isNull())).thenReturn(file);
        when(service.read(eq(5L),eq(11L),isNull())).thenReturn(file);
        var controller=new ExamScheduleAttachmentController(); ReflectionTestUtils.setField(controller,"service",service);
        var mvc=MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(multipart("/zsjos/exam-calendar/attachment/upload").file(new MockMultipartFile("file","官方通知.pdf","application/pdf",new byte[]{1})))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data.fileId").value(11))
                .andExpect(jsonPath("$.data.name").value("官方通知.pdf"));
        mvc.perform(get("/zsjos/exam-calendar/attachment/5/11")).andExpect(status().isOk()).andExpect(jsonPath("$.data.url").value(file.getUrl()));
        mvc.perform(multipart("/zsjos/exam-calendar/attachment/upload")).andExpect(status().isBadRequest());
        verify(service,times(1)).upload(any(),isNull());
    }
    @Test void bindsConfiguredPermissionsToEndpoints() throws Exception {
        assertEquals("@ss.hasPermission('zsjos:exam-calendar:manage')",ExamScheduleAttachmentController.class.getMethod("upload",MultipartFile.class).getAnnotation(PreAuthorize.class).value());
        assertEquals("@ss.hasPermission('zsjos:exam-calendar:query')",ExamScheduleAttachmentController.class.getMethod("read",Long.class,Long.class).getAnnotation(PreAuthorize.class).value());
    }
}
