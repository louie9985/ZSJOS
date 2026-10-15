package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.*;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskCommandService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@ExtendWith(MockitoExtension.class)
class RegistrationEarlyDeliveryTest {
    @InjectMocks RegistrationServiceImpl service;
    @Mock RegistrationCaseMapper caseMapper;
    @Mock SalesOrderMapper orderMapper;
    @Mock SalesOrderItemMapper orderItemMapper;
    @Mock RegistrationCommandMapper commandMapper;
    @Mock RegistrationCaseChecklistItemMapper caseItemMapper;
    @Mock RegistrationItemAttachmentMapper attachmentMapper;
    @Mock RegistrationItemMapper registrationItemMapper;
    @Mock ServiceRelationMapper serviceRelationMapper;
    @Mock RegistrationClassAssignmentMapper classAssignmentMapper;
    @Mock RegistrationNotifyPublisher registrationNotifyPublisher;
    @Mock BusinessTaskCommandService taskCommandService;
    @Mock StudentContactExtensionMapper studentContactExtensionMapper;
    RegistrationCaseDO task;
    SalesOrderDO order;
    ServiceRelationDO relation;

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        task = new RegistrationCaseDO(); task.setId(1L); task.setOrderId(10L); task.setOrderItemId(101L);
        task.setStatus("processing"); task.setAssignmentMode("class_per_item"); task.setVersion(3);
        order = new SalesOrderDO(); order.setId(10L); order.setPersonId(1000L); order.setStatus("effective");
        relation = new ServiceRelationDO(); relation.setId(500L); relation.setRegistrationCaseId(1L);
        relation.setOrderId(10L); relation.setOrderItemId(101L); relation.setPersonId(1000L);
        relation.setStatus("active"); relation.setVersion(8); relation.setOwnerUserId(20L);
        relation.setAcceptanceStatus("accepted"); relation.setClassId(900L);
        lenient().when(caseMapper.selectById(1L)).thenReturn(task);
        lenient().when(caseMapper.selectByIdForUpdate(1L,1L)).thenReturn(task);
        lenient().when(orderMapper.selectByIdForUpdate(10L,1L)).thenReturn(order);
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }

    @ParameterizedTest @ValueSource(strings={"active","paused","completed"})
    void completionPreservesAcceptedOrTransferredService(String status) {
        relation.setStatus(status);
        var check = new RegistrationCaseChecklistItemDO(); check.setId(2L); check.setItemType("checkbox"); check.setChecked(true);
        when(caseItemMapper.selectByCaseId(1L)).thenReturn(List.of(check));
        when(serviceRelationMapper.selectByCaseForUpdate(1L,1L)).thenReturn(List.of(relation));
        var item = new SalesOrderItemDO(); item.setId(101L);
        when(orderItemMapper.selectListByOrderIdForUpdate(10L,1L)).thenReturn(List.of(item));
        var req = new RegistrationVersionReqVO(); req.setVersion(3); req.setIdempotencyKey("complete-existing");
        service.complete(1L,7L,req);
        assertEquals("completed",task.getStatus());
        assertEquals("accepted",relation.getAcceptanceStatus()); assertEquals(900L,relation.getClassId());
        assertEquals(8,relation.getVersion());
        verify(serviceRelationMapper,never()).insert(any(ServiceRelationDO.class));
        verifyNoInteractions(classAssignmentMapper,registrationNotifyPublisher);
    }

    @Test void pendingFinanceCannotCompleteEvenAfterDelivery() {
        order.setStatus("pending_approval");
        var req = new RegistrationVersionReqVO(); req.setVersion(3); req.setIdempotencyKey("pending");
        assertEquals(REGISTRATION_FINANCE_PENDING.getCode(),assertThrows(ServiceException.class,()->service.complete(1L,7L,req)).getCode());
        verifyNoInteractions(serviceRelationMapper,registrationItemMapper,registrationNotifyPublisher);
    }

    @Test void cancelledServiceCannotBeReactivatedByCompletion() {
        relation.setStatus("cancelled");
        var check = new RegistrationCaseChecklistItemDO(); check.setId(2L); check.setItemType("checkbox"); check.setChecked(true);
        when(caseItemMapper.selectByCaseId(1L)).thenReturn(List.of(check));
        when(serviceRelationMapper.selectByCaseForUpdate(1L,1L)).thenReturn(List.of(relation));
        var item = new SalesOrderItemDO(); item.setId(101L);
        when(orderItemMapper.selectListByOrderIdForUpdate(10L,1L)).thenReturn(List.of(item));
        var req = new RegistrationVersionReqVO(); req.setVersion(3); req.setIdempotencyKey("cancelled");
        assertEquals(REGISTRATION_DELIVERY_INVALID.getCode(),assertThrows(ServiceException.class,()->service.complete(1L,7L,req)).getCode());
        verify(serviceRelationMapper,never()).insert(any(ServiceRelationDO.class));
    }

    @ParameterizedTest @ValueSource(strings={"revision_required","terminated","superseded"})
    void rejectedOrTerminatedOrderCannotStartDelivery(String status) {
        order.setStatus(status);
        var req = new RegistrationClassAssignmentsSaveReqVO(); req.setVersion(3); req.setIdempotencyKey("blocked");
        var assignment = new RegistrationClassAssignmentsSaveReqVO.AssignmentReqVO(); assignment.setOrderItemId(101L); assignment.setClassId(900L);
        req.setAssignments(List.of(assignment));
        assertThrows(ServiceException.class,()->service.updateClassAssignments(1L,7L,req));
        verifyNoInteractions(serviceRelationMapper,classAssignmentMapper);
    }

    @ParameterizedTest @ValueSource(strings={"active","paused","completed"})
    void productEndCancelsOnlyItsServiceAndPendingTasks(String status) {
        relation.setStatus(status);
        when(serviceRelationMapper.selectByCaseForUpdate(1L,1L)).thenReturn(List.of(relation));
        when(serviceRelationMapper.selectByIdForUpdate(500L,1L)).thenReturn(relation);
        when(serviceRelationMapper.cancelDelivery(eq(500L),eq(8),any(),eq("无需后续交付"))).thenReturn(1);
        var req = new RegistrationCloseReqVO(); req.setVersion(3); req.setIdempotencyKey("end"); req.setReason("无需后续交付");
        service.close(1L,7L,req);
        assertEquals("ended",task.getStatus());
        verify(taskCommandService,times(5)).cancel(anyString(),eq(500L),isNull(),any(),anyString());
        verify(registrationNotifyPublisher).publishDeliveryCancelled(eq(relation),eq("无需后续交付"),any());
        verify(serviceRelationMapper,never()).selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    }

    @Test void orderCancellationUsesCurrentLockedServices() {
        when(serviceRelationMapper.selectByOrderForUpdate(10L,1L)).thenReturn(List.of(relation));
        when(serviceRelationMapper.selectByIdForUpdate(500L,1L)).thenReturn(relation);
        when(serviceRelationMapper.cancelDelivery(eq(500L),eq(8),any(),eq("订单终止"))).thenReturn(1);
        service.cancelByOrderId(10L,"订单终止",LocalDateTime.now());
        var locks = inOrder(orderMapper,serviceRelationMapper);
        locks.verify(orderMapper).selectByIdForUpdate(10L,1L);
        locks.verify(serviceRelationMapper).selectByOrderForUpdate(10L,1L);
        verify(registrationNotifyPublisher).publishDeliveryCancelled(eq(relation),eq("订单终止"),any());
    }

    @Test void staleAssignmentVersionNeverCreatesDelivery() {
        var req = new RegistrationClassAssignmentsSaveReqVO(); req.setVersion(2); req.setIdempotencyKey("stale");
        var assignment = new RegistrationClassAssignmentsSaveReqVO.AssignmentReqVO(); assignment.setOrderItemId(101L); assignment.setClassId(900L);
        req.setAssignments(List.of(assignment));
        assertEquals(REGISTRATION_VERSION_CONFLICT.getCode(), assertThrows(ServiceException.class,
                () -> service.updateClassAssignments(1L,7L,req)).getCode());
        verifyNoInteractions(serviceRelationMapper,classAssignmentMapper);
        var locks = inOrder(orderMapper,caseMapper);
        locks.verify(caseMapper).selectById(1L);
        locks.verify(orderMapper).selectByIdForUpdate(10L,1L);
        locks.verify(caseMapper).selectByIdForUpdate(1L,1L);
    }

    @Test void rejectionRetainsCaseAndRepeatedCancellationDoesNotNotifyAgain() {
        when(caseMapper.selectListByOrderId(10L)).thenReturn(List.of(task));
        when(serviceRelationMapper.selectByCaseForUpdate(1L,1L)).thenReturn(List.of(relation));
        when(serviceRelationMapper.selectByIdForUpdate(500L,1L)).thenReturn(relation);
        when(serviceRelationMapper.cancelDelivery(eq(500L),eq(8),any(),eq("驳回"))).thenReturn(1);
        service.cancelEarlyDeliveryByOrderId(10L,"驳回",LocalDateTime.now());
        relation.setStatus("cancelled");
        service.cancelEarlyDeliveryByOrderId(10L,"驳回",LocalDateTime.now());
        assertEquals("processing",task.getStatus()); verify(caseMapper,never()).updateById(any(RegistrationCaseDO.class));
        verify(registrationNotifyPublisher).publishDeliveryCancelled(eq(relation),eq("驳回"),any());
    }
}
