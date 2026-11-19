package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.DeliveryClassDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderItemDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.product.ZsjosProductDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.RegistrationCaseDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderItemMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.product.ZsjosProductMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.RegistrationCaseMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.service.deliveryclass.AutoDeliveryClassService;
import cn.iocoder.yudao.module.zsjos.service.deliveryclass.DeliveryClassService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.DELIVERY_CLASS_SALES_QUALIFICATION_REQUIRED;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.DELIVERY_CLASS_USER_INVALID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutoClassEnrollmentServiceTest {
    @InjectMocks AutoClassEnrollmentService service;
    @Mock RegistrationService registrationService;
    @Mock RegistrationCaseMapper caseMapper;
    @Mock SalesOrderItemMapper orderItemMapper;
    @Mock ServiceRelationMapper serviceRelationMapper;
    @Mock PersonMapper personMapper;
    @Mock ZsjosProductMapper productMapper;
    @Mock DeliveryClassService deliveryClassService;
    @Mock AutoDeliveryClassService autoClasses;
    @Mock RegistrationNotifyPublisher notifyPublisher;

    private final AdminUserRespDTO owner = new AdminUserRespDTO().setId(31L).setStatus(0).setNickname("班主任").setDeptId(7L);
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 14, 10, 0);

    @BeforeEach void tenant() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test
    void effectiveOrderEnrollsEachProductIntoItsOwnClassWithHomeroomAsOwner() {
        var order = order();
        stubItemsAndCases(List.of(autoCase(1001L, 301L), autoCase(1002L, 302L)));
        when(deliveryClassService.validateHomeroom(31L)).thenReturn(owner);
        when(productMapper.selectByProductRef("P-A")).thenReturn(new ZsjosProductDO().setId(401L).setName("产品A"));
        when(productMapper.selectByProductRef("P-B")).thenReturn(new ZsjosProductDO().setId(402L).setName("产品B"));
        when(autoClasses.ensure(401L, "快照A", "2027-03", owner)).thenReturn(new DeliveryClassDO().setId(801L));
        when(autoClasses.ensure(402L, "产品B", "2027-03", owner)).thenReturn(new DeliveryClassDO().setId(802L));
        var person = new PersonDO(); person.setId(501L); person.setVersion(3); person.setPersonNo("XY1");
        when(personMapper.selectByIdForUpdate(501L, 1L)).thenReturn(person);

        service.enrollOnOrderEffective(order, now);

        verify(registrationService).ensureCaseAfterRegistrationApproval(10L, now);
        var relations = ArgumentCaptor.forClass(ServiceRelationDO.class);
        verify(serviceRelationMapper, times(2)).insert(relations.capture());
        assertEquals(List.of(801L, 802L), relations.getAllValues().stream().map(ServiceRelationDO::getClassId).toList());
        assertEquals(List.of(1001L, 1002L), relations.getAllValues().stream().map(ServiceRelationDO::getRegistrationCaseId).toList());
        relations.getAllValues().forEach(relation -> {
            assertEquals(31L, relation.getOwnerUserId());
            assertEquals("active", relation.getStatus());
            assertEquals("pending", relation.getAcceptanceStatus());
            assertEquals("学员", relation.getStudentNameSnapshot());
        });
        verify(notifyPublisher, times(2)).publishPlannerAssigned(any(), eq(order), eq("XY1"), eq(31L), eq(501L), any());
        assertEquals("student", person.getIdentityStatus());
        verify(personMapper).updateById(person);
    }

    @Test
    void replayKeepsExistingEnrollmentAndCreatesNothing() {
        var order = order();
        stubItemsAndCases(List.of(autoCase(1001L, 301L)));
        when(deliveryClassService.validateHomeroom(31L)).thenReturn(owner);
        when(serviceRelationMapper.selectByCaseForUpdate(1001L, 1L)).thenReturn(List.of(new ServiceRelationDO().setId(900L)));

        service.enrollOnOrderEffective(order, now);

        verify(serviceRelationMapper, never()).insert(any(ServiceRelationDO.class));
        verifyNoInteractions(autoClasses, notifyPublisher);
        verify(personMapper, never()).updateById(any(PersonDO.class));
    }

    @Test
    void legacyModeAndTerminalCasesAreNotEnrolled() {
        var legacy = autoCase(1001L, 301L); legacy.setAssignmentMode("sales_owner");
        var ended = autoCase(1002L, 302L); ended.setStatus("ended");
        stubItemsAndCases(List.of(legacy, ended));
        when(deliveryClassService.validateHomeroom(31L)).thenReturn(owner);

        service.enrollOnOrderEffective(order(), now);

        verifyNoInteractions(autoClasses, serviceRelationMapper, notifyPublisher);
    }

    @Test
    void orderWithoutMonthKeepsLegacyBehavior() {
        service.enrollOnOrderEffective(order().setAgreedExamMonth(null), now);

        verifyNoInteractions(deliveryClassService, registrationService, autoClasses, serviceRelationMapper);
    }

    @Test
    void ineligibleOwnerBlocksApprovalWithActionableQualificationError() {
        when(deliveryClassService.validateHomeroom(31L)).thenThrow(exception(DELIVERY_CLASS_SALES_QUALIFICATION_REQUIRED));

        var error = assertThrows(ServiceException.class, () -> service.enrollOnOrderEffective(order(), now));

        assertEquals(DELIVERY_CLASS_SALES_QUALIFICATION_REQUIRED.getCode(), error.getCode());
        assertTrue(error.getMessage().startsWith("成交负责人缺少销售业务资格"));
        verifyNoInteractions(registrationService, autoClasses, serviceRelationMapper);
    }

    @Test
    void disabledOrMissingOwnerBlocksApprovalWithAccountError() {
        when(deliveryClassService.validateHomeroom(31L)).thenThrow(exception(DELIVERY_CLASS_USER_INVALID));
        var disabled = assertThrows(ServiceException.class, () -> service.enrollOnOrderEffective(order(), now));
        assertEquals(DELIVERY_CLASS_USER_INVALID.getCode(), disabled.getCode());
        assertTrue(disabled.getMessage().startsWith("成交负责人账号不存在或已停用"));

        var missing = assertThrows(ServiceException.class,
                () -> service.enrollOnOrderEffective(order().setFormalSalesUserId(null), now));
        assertEquals(DELIVERY_CLASS_USER_INVALID.getCode(), missing.getCode());
        verify(deliveryClassService, never()).validateHomeroom(null);
        verifyNoInteractions(registrationService, autoClasses, serviceRelationMapper);
    }

    private void stubItemsAndCases(List<RegistrationCaseDO> cases) {
        var first = new SalesOrderItemDO(); first.setId(301L); first.setProductRef("P-A");
        first.setProductSnapshot("{\"name\":\"快照A\"}");
        var second = new SalesOrderItemDO(); second.setId(302L); second.setProductRef("P-B");
        lenient().when(orderItemMapper.selectListByOrderIdForUpdate(10L, 1L)).thenReturn(List.of(first, second));
        when(caseMapper.selectListByOrderId(10L)).thenReturn(cases);
        lenient().when(serviceRelationMapper.selectByCaseForUpdate(anyLong(), eq(1L))).thenReturn(List.of());
    }

    private static SalesOrderDO order() {
        return new SalesOrderDO().setId(10L).setPersonId(501L).setFormalSalesUserId(31L)
                .setStudentName(" 学员 ").setAgreedExamMonth("2027-03");
    }

    private static RegistrationCaseDO autoCase(Long id, Long orderItemId) {
        var row = new RegistrationCaseDO();
        row.setId(id); row.setOrderId(10L); row.setOrderItemId(orderItemId); row.setStatus("pending");
        row.setAssignmentMode(AutoClassEnrollmentService.ASSIGNMENT_MODE);
        return row;
    }
}
