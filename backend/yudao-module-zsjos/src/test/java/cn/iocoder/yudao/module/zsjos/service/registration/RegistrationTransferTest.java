package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.RegistrationTransferReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.RegistrationCaseDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.RegistrationCommandDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.RegistrationCaseMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.RegistrationCommandMapper;
import cn.iocoder.yudao.module.zsjos.service.sales.SalesDepartmentBoundaryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationTransferTest {
    @InjectMocks private RegistrationServiceImpl service;
    @Mock private RegistrationCaseMapper caseMapper;
    @Mock private SalesOrderMapper orderMapper;
    @Mock private RegistrationCommandMapper commandMapper;
    @Mock private RegistrationCaseObjectPermissionProvider casePermission;
    @Mock private PermissionApi permissionApi;
    @Mock private AdminUserApi adminUserApi;
    @Mock private SalesDepartmentBoundaryService salesBoundary;
    @Mock private RegistrationNotifyPublisher registrationNotifyPublisher;

    private final RegistrationCaseDO registration = new RegistrationCaseDO();
    private final SalesOrderDO order = new SalesOrderDO();
    private final RegistrationTransferReqVO request = new RegistrationTransferReqVO();

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        registration.setId(10L);
        registration.setOrderId(42L);
        registration.setOwnerUserId(7L);
        registration.setVersion(2);
        order.setId(42L);
        order.setFormalSalesUserId(7L);
        request.setTargetUserId(8L);
        request.setVersion(2);
        request.setIdempotencyKey("transfer-fixture");
        request.setReason("Coverage handoff");
        when(permissionApi.hasAnyPermissions(7L, "zsjos:registration:transfer")).thenReturn(true);
        when(caseMapper.selectById(10L)).thenReturn(registration);
        when(caseMapper.selectByIdForUpdate(10L, 1L)).thenReturn(registration);
    }

    @AfterEach void clear() {
        TenantContextHolder.clear();
    }

    @Test void writesSingleAuditAndReplaysWithoutChangingFormalOwner() {
        when(adminUserApi.getUser(8L)).thenReturn(new AdminUserRespDTO().setId(8L).setStatus(0));
        when(permissionApi.hasAnyPermissions(8L, "zsjos:registration:query-my")).thenReturn(true);
        when(salesBoundary.canTransferTo(7L, 8L)).thenReturn(true);
        when(orderMapper.selectById(42L)).thenReturn(order);

        var result = service.transfer(10L, 7L, request);
        assertEquals(8L, result.getOwnerUserId());
        assertEquals(3, result.getVersion());
        assertEquals(8L, registration.getOwnerUserId());
        assertEquals(7L, order.getFormalSalesUserId());

        var audit = ArgumentCaptor.forClass(RegistrationCommandDO.class);
        verify(commandMapper).insert(audit.capture());
        assertEquals(7L, audit.getValue().getOperatorUserId());
        assertEquals(10L, audit.getValue().getRegistrationCaseId());
        assertEquals("transfer", audit.getValue().getCommandType());
        assertEquals("7:8:2:Coverage handoff", audit.getValue().getRequestFingerprint());
        when(commandMapper.selectByIdempotencyKey("transfer-fixture")).thenReturn(audit.getValue());
        assertEquals(3, service.transfer(10L, 7L, request).getVersion());

        request.setReason("Different request");
        assertEquals(REGISTRATION_IDEMPOTENCY_CONFLICT.getCode(),
                assertThrows(ServiceException.class, () -> service.transfer(10L, 7L, request)).getCode());
        verify(caseMapper).updateById(registration);
        verify(registrationNotifyPublisher).publishTaskTransferred(registration, order);
    }

    @Test void rejectsStaleVersionAndUnauthorizedOwnerWithoutWrites() {
        registration.setVersion(3);
        assertEquals(REGISTRATION_VERSION_CONFLICT.getCode(),
                assertThrows(ServiceException.class, () -> service.transfer(10L, 7L, request)).getCode());

        doThrow(exception(REGISTRATION_PERMISSION_DENIED)).when(casePermission).checkCase(registration, "transfer", 7L);
        assertEquals(REGISTRATION_PERMISSION_DENIED.getCode(),
                assertThrows(ServiceException.class, () -> service.transfer(10L, 7L, request)).getCode());
        verify(caseMapper, never()).updateById(any(RegistrationCaseDO.class));
        verify(commandMapper, never()).insert(any(RegistrationCommandDO.class));
        verifyNoInteractions(adminUserApi, registrationNotifyPublisher);
    }
}
