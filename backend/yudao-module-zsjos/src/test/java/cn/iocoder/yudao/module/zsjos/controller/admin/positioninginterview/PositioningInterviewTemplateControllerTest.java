package cn.iocoder.yudao.module.zsjos.controller.admin.positioninginterview;

import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.zsjos.controller.admin.director.DirectorFormTemplateController;
import cn.iocoder.yudao.module.zsjos.controller.admin.director.vo.DirectorFormTemplateVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.StudentContactController;
import cn.iocoder.yudao.module.zsjos.service.director.DirectorFormTemplateService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static cn.iocoder.yudao.module.zsjos.service.director.DirectorFormTemplateService.SCENE_POSITIONING_INTERVIEW;

class PositioningInterviewTemplateControllerTest {
    @Configuration @EnableMethodSecurity
    static class Config {
        @Bean(name = "ss") SecurityFrameworkService security() { return mock(SecurityFrameworkService.class); }
        @Bean PositioningInterviewTemplateController controller() { return new PositioningInterviewTemplateController(); }
    }

    @Test
    void queryUpdateAndPublishUseIndependentConfiguredPermissions() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getBeanFactory().registerSingleton("service", mock(DirectorFormTemplateService.class));
            context.register(Config.class);
            context.refresh();
            var controller = context.getBean(PositioningInterviewTemplateController.class);
            var security = context.getBean(SecurityFrameworkService.class);
            var service = context.getBean(DirectorFormTemplateService.class);
            assertThrows(AccessDeniedException.class, controller::list);
            verifyNoInteractions(service);
            when(security.hasPermission("zsjos:director-interview-template:query")).thenReturn(true);
            when(service.list(SCENE_POSITIONING_INTERVIEW)).thenReturn(List.of());
            assertEquals(List.of(), controller.list().getData());
            assertThrows(AccessDeniedException.class, () -> controller.copy(10L, 0));
            when(security.hasPermission("zsjos:director-interview-template:update")).thenReturn(true);
            controller.copy(10L, 0);
            verify(service).copyDraft(10L, 0, SCENE_POSITIONING_INTERVIEW);
            var request = new DirectorFormTemplateVO.PublishReq();
            assertThrows(AccessDeniedException.class, () -> controller.publish(10L, request));
            when(security.hasPermission("zsjos:director-interview-template:publish")).thenReturn(true);
            controller.publish(10L, request);
            verify(service).publish(eq(10L), same(request), isNull(), eq(SCENE_POSITIONING_INTERVIEW));
        }
    }

    @Test
    void retiredQuestionnaireRoutesAreAbsentButNewTemplateRouteIsDiscoverable() throws Exception {
        var current = new PositioningInterviewTemplateController();
        var service = mock(DirectorFormTemplateService.class);
        ReflectionTestUtils.setField(current, "service", service);
        when(service.list(SCENE_POSITIONING_INTERVIEW)).thenReturn(List.of());
        var mvc = MockMvcBuilders.standaloneSetup(current, new DirectorFormTemplateController(),
                new StudentContactController()).build();
        mvc.perform(get("/zsjos/positioning-interview-template/list"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(get("/zsjos/director-interview-template/list")).andExpect(status().isNotFound());
        mvc.perform(post("/zsjos/director-interview-template/10/draft/copy")).andExpect(status().isNotFound());
        mvc.perform(put("/zsjos/director-interview-template/10/draft")).andExpect(status().isNotFound());
        mvc.perform(post("/zsjos/director-interview-template/10/publish")).andExpect(status().isNotFound());
        mvc.perform(post("/zsjos/student/service/9/interview/draft")).andExpect(status().isNotFound());
        mvc.perform(post("/zsjos/student/service/9/interview/submit")).andExpect(status().isNotFound());
    }
}
