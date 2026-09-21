package cn.iocoder.yudao.module.zsjos.controller.admin.workorder;

import cn.iocoder.yudao.module.zsjos.controller.admin.production.vo.ProductionTicketSaveReqVO;
import cn.iocoder.yudao.module.zsjos.service.production.ProductionTicketService;
import cn.iocoder.yudao.module.zsjos.service.workorder.WorkOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 账号绑定只在账号页强制；工单中心直接发起的剪拍工单不再要求 relatedAccountId。
 */
class WorkOrderCreateAccountBindingTest {
    private WorkOrderService service;
    private ProductionTicketService productionTicketService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(WorkOrderService.class);
        productionTicketService = mock(ProductionTicketService.class);
        WorkOrderController controller = new WorkOrderController();
        ReflectionTestUtils.setField(controller, "service", service);
        ReflectionTestUtils.setField(controller, "productionTicketService", productionTicketService);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void productionTicketFromWorkOrderCentreMayOmitAccount() throws Exception {
        when(service.isProductionTemplate("media_design_edit", null)).thenReturn(true);
        when(productionTicketService.createFromWorkOrder(any(ProductionTicketSaveReqVO.class), nullable(Long.class)))
                .thenReturn(100L);

        mvc.perform(post("/zsjos/work-order/create").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sceneCode\":\"media_design_edit\",\"remark\":\"加急\",\"idempotencyKey\":\"k1\",\"values\":{}}"))
                .andExpect(status().isOk());

        var captor = org.mockito.ArgumentCaptor.forClass(ProductionTicketSaveReqVO.class);
        verify(productionTicketService).createFromWorkOrder(captor.capture(), nullable(Long.class));
        // 未绑定账号时传 null，由 service 走无账号分支而不是报错。
        org.junit.jupiter.api.Assertions.assertNull(captor.getValue().getAccountId());
    }

    @Test
    void productionTicketStillForwardsChosenAccount() throws Exception {
        when(service.isProductionTemplate("media_design_edit", null)).thenReturn(true);
        when(productionTicketService.createFromWorkOrder(any(ProductionTicketSaveReqVO.class), nullable(Long.class)))
                .thenReturn(100L);

        mvc.perform(post("/zsjos/work-order/create").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sceneCode\":\"media_design_edit\",\"remark\":\"加急\",\"relatedAccountId\":7,\"idempotencyKey\":\"k2\",\"values\":{}}"))
                .andExpect(status().isOk());

        var captor = org.mockito.ArgumentCaptor.forClass(ProductionTicketSaveReqVO.class);
        verify(productionTicketService).createFromWorkOrder(captor.capture(), nullable(Long.class));
        org.junit.jupiter.api.Assertions.assertEquals(7L, captor.getValue().getAccountId());
    }

    @Test
    void genericTemplateDoesNotTouchProductionTickets() throws Exception {
        when(service.isProductionTemplate("generic", null)).thenReturn(false);

        mvc.perform(post("/zsjos/work-order/create").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sceneCode\":\"generic\",\"remark\":\"通用\",\"idempotencyKey\":\"k3\",\"values\":{}}"))
                .andExpect(status().isOk());

        verify(service).create(any(), nullable(Long.class));
        verifyNoInteractions(productionTicketService);
    }
}
