package cn.iocoder.yudao.module.zsjos.service.order;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.notify.NotifyBusinessEventApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderApprovalRoundDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderApprovalConfigMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderApprovalRoundMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.zsjos.service.registration.AutoClassEnrollmentService;
import cn.iocoder.yudao.module.zsjos.service.registration.RegistrationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum.APPROVE;
import static cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.DELIVERY_CLASS_SALES_QUALIFICATION_REQUIRED;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** The order-effective transition is the only trigger of automatic class enrollment. */
@ExtendWith(MockitoExtension.class)
class SalesOrderEffectiveEnrollmentTest {
    @InjectMocks SalesOrderServiceImpl service;
    @Mock SalesOrderMapper orderMapper;
    @Mock SalesOrderApprovalRoundMapper roundMapper;
    @Mock SalesOrderApprovalConfigMapper configMapper;
    @Mock SalesOrderSupervisorConfirmationService supervisorConfirmationService;
    @Mock NotifyBusinessEventApi notifyBusinessEventApi;
    @Mock DeptApi deptApi;
    @Mock RegistrationService registrationService;
    @Mock AutoClassEnrollmentService autoClassEnrollmentService;

    private SalesOrderDO order;

    @BeforeEach void setUp() {
        TenantContextHolder.setTenantId(1L);
        SalesOrderApprovalRoundDO round = new SalesOrderApprovalRoundDO();
        round.setId(200L); round.setOrderId(100L); round.setStatus(ROUND_PENDING); round.setProcessInstanceId("process-1");
        order = new SalesOrderDO();
        order.setId(100L); order.setOrderType(ORDER_TYPE_REPURCHASE); order.setPersonId(501L); order.setFormalSalesUserId(31L);
        order.setCurrentApprovalRoundId(200L); order.setStatus(STATUS_PENDING_APPROVAL); order.setAgreedExamMonth("2027-03");
        when(roundMapper.selectByProcessInstanceId("process-1")).thenReturn(round);
        when(roundMapper.selectByIdForUpdate(200L, 1L)).thenReturn(round);
        when(orderMapper.selectByIdForUpdate(100L, 1L)).thenReturn(order);
    }

    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test
    void approvalEnrollsAfterOrderIsPersistedEffective() {
        service.handleProcessResult("process-1", APPROVE.getStatus(), null);

        assertEquals(STATUS_EFFECTIVE, order.getStatus());
        var ordered = inOrder(orderMapper, autoClassEnrollmentService);
        ordered.verify(orderMapper).updateById(order);
        ordered.verify(autoClassEnrollmentService).enrollOnOrderEffective(eq(order), any());
    }

    @Test
    void enrollmentFailurePropagatesSoTheApprovalRollsBack() {
        doThrow(exception(DELIVERY_CLASS_SALES_QUALIFICATION_REQUIRED))
                .when(autoClassEnrollmentService).enrollOnOrderEffective(eq(order), any());

        var error = assertThrows(ServiceException.class,
                () -> service.handleProcessResult("process-1", APPROVE.getStatus(), null));

        assertEquals(DELIVERY_CLASS_SALES_QUALIFICATION_REQUIRED.getCode(), error.getCode());
        verifyNoInteractions(notifyBusinessEventApi);
    }

    @Test
    void legacyOrderWithoutMonthIsNotAutoEnrolled() {
        order.setAgreedExamMonth(null);

        service.handleProcessResult("process-1", APPROVE.getStatus(), null);

        assertEquals(STATUS_EFFECTIVE, order.getStatus());
        verifyNoInteractions(autoClassEnrollmentService);
    }
}
