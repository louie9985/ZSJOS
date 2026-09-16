package cn.iocoder.yudao.module.zsjos.controller.admin.workorder;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.service.workorder.WorkOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorkOrderPoolControllerTest {
    private WorkOrderService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(WorkOrderService.class);
        WorkOrderController controller = new WorkOrderController();
        ReflectionTestUtils.setField(controller, "service", service);
        when(service.pool(nullable(String.class), anyInt(), anyInt(), nullable(Long.class)))
                .thenReturn(PageResult.empty());
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void allowsOmittedSceneForAvailablePage() throws Exception {
        mvc.perform(get("/zsjos/work-order/pool").param("pageNo", "1").param("pageSize", "20"))
                .andExpect(status().isOk());
        verify(service).pool(isNull(), eq(1), eq(20), nullable(Long.class));
    }

    @Test
    void preservesExplicitSceneFilter() throws Exception {
        mvc.perform(get("/zsjos/work-order/pool").param("sceneCode", "filming_field_work")
                        .param("pageNo", "2").param("pageSize", "30"))
                .andExpect(status().isOk());
        verify(service).pool(eq("filming_field_work"), eq(2), eq(30), nullable(Long.class));
    }

    @Test
    void rejectsOversizedScene() throws Exception {
        mvc.perform(get("/zsjos/work-order/pool").param("sceneCode", "x".repeat(65)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void retainsPaginationValidationWithoutScene() throws Exception {
        mvc.perform(get("/zsjos/work-order/pool").param("pageNo", "0"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
