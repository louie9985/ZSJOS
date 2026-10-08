package cn.iocoder.yudao.module.zsjos.controller.admin.examcalendar;

import cn.iocoder.yudao.module.zsjos.service.examcalendar.ExamScheduleService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExamScheduleColorControllerTest {
    @Test void httpValidatesColorBeforeCallingService() throws Exception {
        var service = mock(ExamScheduleService.class);
        var controller = new ExamScheduleController(); ReflectionTestUtils.setField(controller, "service", service);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        for (String color : new String[]{"red", "#fff", "#12345678", "url(example)"}) {
            mvc.perform(post("/zsjos/exam-calendar/create").contentType(MediaType.APPLICATION_JSON)
                .content("{\"scheduleName\":\"颜色考试\",\"scheduleType\":\"EXACT\",\"exactDate\":\"2099-10-10\",\"backgroundColor\":\"" + color + "\"}"))
                .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
        for (String color : new String[]{"#AABBCC", ""}) {
            mvc.perform(post("/zsjos/exam-calendar/create").contentType(MediaType.APPLICATION_JSON)
                .content("{\"scheduleName\":\"颜色考试\",\"scheduleType\":\"EXACT\",\"exactDate\":\"2099-10-10\",\"backgroundColor\":\"" + color + "\"}"))
                .andExpect(status().isOk());
        }
        verify(service, times(2)).create(any(), isNull());
    }
}
