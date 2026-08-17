package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.RegistrationCaseRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.RegistrationVersionReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.RegistrationCaseChecklistItemDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.RegistrationCaseDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.RegistrationCaseChecklistItemMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.RegistrationCaseMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.RegistrationCommandMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static cn.iocoder.yudao.module.zsjos.service.registration.RegistrationConstants.STATUS_PENDING;
import static cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.STATUS_PENDING_APPROVAL;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.REGISTRATION_FINANCE_PENDING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceImplTest {

    @InjectMocks private RegistrationServiceImpl service;
    @Mock private RegistrationCaseMapper caseMapper;
    @Mock private RegistrationCaseChecklistItemMapper caseItemMapper;
    @Mock private SalesOrderMapper orderMapper;
    @Mock private RegistrationCommandMapper commandMapper;

    @Test
    void getCaseAllowsUncheckedItemWithoutOperator() {
        RegistrationCaseDO registrationCase = new RegistrationCaseDO();
        registrationCase.setId(1L);
        registrationCase.setOrderId(10L);
        registrationCase.setStatus(STATUS_PENDING);
        registrationCase.setVersion(0);
        RegistrationCaseChecklistItemDO item = new RegistrationCaseChecklistItemDO();
        item.setId(100L);
        item.setRegistrationCaseId(1L);
        item.setChecked(false);

        when(caseMapper.selectById(1L)).thenReturn(registrationCase);
        when(caseItemMapper.selectByCaseId(1L)).thenReturn(List.of(item));

        RegistrationCaseRespVO result = service.getCase(1L);

        assertFalse(result.getCompletable());
        assertNull(result.getItems().getFirst().getCheckedByUserName());
    }

    @Test
    void getCaseExplainsFinanceApprovalBlockInChinese() {
        RegistrationCaseDO registrationCase = new RegistrationCaseDO();
        registrationCase.setId(1L); registrationCase.setOrderId(10L);
        registrationCase.setStatus(STATUS_PENDING); registrationCase.setVersion(0);
        SalesOrderDO order = new SalesOrderDO();
        order.setId(10L); order.setStatus(STATUS_PENDING_APPROVAL);
        when(caseMapper.selectById(1L)).thenReturn(registrationCase);
        when(caseItemMapper.selectByCaseId(1L)).thenReturn(List.of());
        when(orderMapper.selectById(10L)).thenReturn(order);

        RegistrationCaseRespVO result = service.getCase(1L);

        assertEquals("待处理", result.getStatusLabel());
        assertEquals("待财务审核", result.getOrderStatusLabel());
        assertEquals("财务审核通过后才能完成报名履约", result.getCompletionBlockReason());
        assertFalse(result.getCompletable());
    }

    @Test
    void completeReturnsDistinctFinancePendingError() {
        RegistrationCaseDO registrationCase = new RegistrationCaseDO();
        registrationCase.setId(1L); registrationCase.setOrderId(10L);
        registrationCase.setStatus(STATUS_PENDING); registrationCase.setVersion(0);
        SalesOrderDO order = new SalesOrderDO();
        order.setId(10L); order.setStatus(STATUS_PENDING_APPROVAL);
        when(caseMapper.selectByIdForUpdate(1L, 1L)).thenReturn(registrationCase);
        when(orderMapper.selectById(10L)).thenReturn(order);
        RegistrationVersionReqVO request = new RegistrationVersionReqVO();
        request.setVersion(0); request.setIdempotencyKey("complete-finance-pending");

        cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(1L);
        try {
            ServiceException error = assertThrows(ServiceException.class, () -> service.complete(1L, 9L, request));
            assertEquals(REGISTRATION_FINANCE_PENDING.getCode(), error.getCode());
        } finally {
            cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear();
        }
    }
}
