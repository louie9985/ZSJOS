package cn.iocoder.yudao.module.zsjos.controller.admin.examcalendar;

import cn.iocoder.yudao.module.zsjos.controller.admin.examcalendar.vo.ExamScheduleReeditRespVO;
import cn.iocoder.yudao.module.zsjos.service.examcalendar.ExamScheduleService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import static cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ExamScheduleReeditControllerTest {
    @Test void postReturnsEditableContentWithoutExposingPersistenceFields() throws Exception {
        var service = mock(ExamScheduleService.class);
        when(service.reedit(eq(9L), eq("request-key-00001"), isNull()))
            .thenReturn(new ExamScheduleReeditRespVO().setScheduleName("原始考试").setScheduleType("EXACT").setRemark("原备注"));
        var controller = new ExamScheduleController(); ReflectionTestUtils.setField(controller, "service", service);
        MockMvcBuilders.standaloneSetup(controller).build().perform(post("/zsjos/exam-calendar/reedit/9")
                .contentType(MediaType.APPLICATION_JSON).content(toJsonString(Map.of("operationKey", "request-key-00001"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.scheduleName").value("原始考试"))
            .andExpect(jsonPath("$.data.id").doesNotExist()).andExpect(jsonPath("$.data.revokedBy").doesNotExist());
    }
    @Test void missingOrMalformedKeysFailAtHttpBoundary() throws Exception {
        var service = mock(ExamScheduleService.class);
        var controller = new ExamScheduleController(); ReflectionTestUtils.setField(controller, "service", service);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        for (String body : new String[]{"{}", toJsonString(Map.of("operationKey", "tiny")), toJsonString(Map.of("operationKey", "has invalid characters !"))}) {
            mvc.perform(post("/zsjos/exam-calendar/reedit/9").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }
}
