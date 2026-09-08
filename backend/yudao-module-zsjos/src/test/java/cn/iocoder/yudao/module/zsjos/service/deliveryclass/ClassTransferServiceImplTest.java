package cn.iocoder.yudao.module.zsjos.service.deliveryclass;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.ClassTransferRequestDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.DeliveryClassDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.ClassTransferRequestMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.DeliveryClassMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClassTransferServiceImplTest {
    @InjectMocks private ClassTransferServiceImpl service;
    @Mock private ClassTransferRequestMapper requestMapper;
    @Mock private ServiceRelationMapper relationMapper;
    @Mock private DeliveryClassMapper classMapper;
    @Mock private AdminUserApi adminUserApi;
    @Mock private PermissionApi permissionApi;
    @Mock private DeliveryClassServiceImpl deliveryClassService;

    @BeforeEach void setUp() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void tearDown() { TenantContextHolder.clear(); }

    @Test
    void approvedResultTransfersOnceAndFinishesRequest() {
        ClassTransferRequestDO request = pendingRequest();
        ServiceRelationDO relation = new ServiceRelationDO();
        relation.setId(10L); relation.setClassId(100L); relation.setOwnerUserId(11L);
        relation.setStatus("active"); relation.setVersion(3);
        DeliveryClassDO source = sourceClass();
        DeliveryClassDO target = targetClass();
        when(requestMapper.selectByProcessInstanceId("process-1")).thenReturn(request);
        when(requestMapper.selectByIdForUpdate(1L, 1L)).thenReturn(request);
        when(relationMapper.selectByIdForUpdate(10L, 1L)).thenReturn(relation);
        when(classMapper.selectById(100L)).thenReturn(source);
        when(classMapper.selectByIdForUpdate(200L, 1L)).thenReturn(target);
        when(adminUserApi.getUser(20L)).thenReturn(new AdminUserRespDTO().setId(20L).setStatus(0));
        when(permissionApi.hasAnyPermissions(20L, DeliveryClassService.PERMISSION_QUERY_MY)).thenReturn(true);
        when(permissionApi.hasAnyPermissions(20L, "zsjos:student:query-my")).thenReturn(true);

        service.handleProcessResult("process-1", BpmProcessInstanceStatusEnum.APPROVE.getStatus(), "同意");

        verify(deliveryClassService).transferApproved(relation, target, "class-transfer-approved:1", "申请原因");
        assertEquals("approved", request.getStatus());
        assertEquals("同意", request.getResolutionReason());
        verify(requestMapper).updateById(request);
    }

    @Test
    void staleApprovalInvalidatesWithoutMovingOwnership() {
        ClassTransferRequestDO request = pendingRequest();
        ServiceRelationDO relation = new ServiceRelationDO();
        relation.setId(10L); relation.setClassId(999L); relation.setOwnerUserId(11L);
        relation.setStatus("active"); relation.setVersion(3);
        when(requestMapper.selectByProcessInstanceId("process-1")).thenReturn(request);
        when(requestMapper.selectByIdForUpdate(1L, 1L)).thenReturn(request);
        when(relationMapper.selectByIdForUpdate(10L, 1L)).thenReturn(relation);
        when(classMapper.selectById(100L)).thenReturn(sourceClass());
        when(classMapper.selectByIdForUpdate(200L, 1L)).thenReturn(targetClass());
        when(adminUserApi.getUser(20L)).thenReturn(new AdminUserRespDTO().setId(20L).setStatus(0));
        when(permissionApi.hasAnyPermissions(20L, DeliveryClassService.PERMISSION_QUERY_MY)).thenReturn(true);
        when(permissionApi.hasAnyPermissions(20L, "zsjos:student:query-my")).thenReturn(true);

        service.handleProcessResult("process-1", BpmProcessInstanceStatusEnum.APPROVE.getStatus(), "同意");

        assertEquals("invalidated", request.getStatus());
        verifyNoInteractions(deliveryClassService);
    }

    @Test
    void duplicateTerminalEventHasNoSideEffects() {
        ClassTransferRequestDO request = pendingRequest();
        request.setStatus("approved");
        when(requestMapper.selectByProcessInstanceId("process-1")).thenReturn(request);
        when(requestMapper.selectByIdForUpdate(1L, 1L)).thenReturn(request);

        service.handleProcessResult("process-1", BpmProcessInstanceStatusEnum.APPROVE.getStatus(), "重复事件");

        verifyNoInteractions(relationMapper, classMapper, adminUserApi, deliveryClassService);
        verify(requestMapper, never()).updateById(any(ClassTransferRequestDO.class));
    }

    @Test
    void ineligibleTargetHomeroomInvalidatesApproval() {
        ClassTransferRequestDO request = pendingRequest();
        ServiceRelationDO relation = new ServiceRelationDO();
        relation.setId(10L); relation.setClassId(100L); relation.setOwnerUserId(11L);
        relation.setStatus("active"); relation.setVersion(3);
        when(requestMapper.selectByProcessInstanceId("process-1")).thenReturn(request);
        when(requestMapper.selectByIdForUpdate(1L, 1L)).thenReturn(request);
        when(relationMapper.selectByIdForUpdate(10L, 1L)).thenReturn(relation);
        when(classMapper.selectById(100L)).thenReturn(sourceClass());
        when(classMapper.selectByIdForUpdate(200L, 1L)).thenReturn(targetClass());
        when(adminUserApi.getUser(20L)).thenReturn(new AdminUserRespDTO().setId(20L).setStatus(0));
        when(permissionApi.hasAnyPermissions(20L, DeliveryClassService.PERMISSION_QUERY_MY)).thenReturn(false);

        service.handleProcessResult("process-1", BpmProcessInstanceStatusEnum.APPROVE.getStatus(), "同意");

        assertEquals("invalidated", request.getStatus());
        verifyNoInteractions(deliveryClassService);
    }

    private static ClassTransferRequestDO pendingRequest() {
        ClassTransferRequestDO request = new ClassTransferRequestDO();
        request.setId(1L); request.setTenantId(1L); request.setServiceRelationId(10L);
        request.setServiceRelationVersion(3); request.setFromClassId(100L); request.setTargetClassId(200L);
        request.setFromHomeroomUserId(11L); request.setTargetHomeroomUserId(20L);
        request.setReason("申请原因"); request.setStatus("pending"); request.setVersion(0);
        return request;
    }

    private static DeliveryClassDO targetClass() {
        DeliveryClassDO target = new DeliveryClassDO();
        target.setId(200L); target.setSystemClass(false); target.setStatus("SERVING");
        target.setCategoryId(300L); target.setHomeroomUserId(20L);
        return target;
    }

    private static DeliveryClassDO sourceClass() {
        DeliveryClassDO source = new DeliveryClassDO();
        source.setId(100L); source.setSystemClass(false); source.setCategoryId(300L);
        return source;
    }
}
