package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.RegistrationVersionReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderItemDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderItemMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.*;
import cn.iocoder.yudao.module.zsjos.service.studentinfo.StudentInfoTaskService;
import cn.iocoder.yudao.module.zsjos.service.deliveryclass.UnscheduledDeliveryClassService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.STATUS_EFFECTIVE;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.REGISTRATION_DELIVERY_INVALID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Case mode selection and the material-only completion contract of auto-class fulfillment. */
@ExtendWith(MockitoExtension.class)
class RegistrationAutoClassModeTest {
    @InjectMocks RegistrationServiceImpl service;
    @Mock private RegistrationCaseObjectPermissionProvider casePermission;
    @Mock RegistrationCaseMapper caseMapper;
    @Mock SalesOrderMapper orderMapper;
    @Mock SalesOrderItemMapper orderItemMapper;
    @Mock RegistrationChecklistTemplateMapper templateMapper;
    @Mock RegistrationChecklistTemplateItemMapper templateItemMapper;
    @Mock RegistrationRouteOptionMapper routeOptionMapper;
    @Mock RegistrationCaseChecklistItemMapper caseItemMapper;
    @Mock RegistrationCaseRouteMapper caseRouteMapper;
    @Mock RegistrationNotifyPublisher registrationNotifyPublisher;
    @Mock RegistrationCommandMapper commandMapper;
    @Mock RegistrationItemAttachmentMapper attachmentMapper;
    @Mock RegistrationItemMapper registrationItemMapper;
    @Mock ServiceRelationMapper serviceRelationMapper;
    @Mock StudentInfoTaskService studentInfoTaskService;
    @Mock UnscheduledDeliveryClassService unscheduledClasses;

    @BeforeEach void tenant() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void clear() { TenantContextHolder.clear(); }

    @ParameterizedTest
    @CsvSource(value = {"2027-03,auto_class", "NULL,sales_owner"}, nullValues = "NULL")
    void caseModeFollowsAgreedExamMonth(String month, String expectedMode) {
        var order = new SalesOrderDO().setId(10L).setAgreedExamMonth(month);
        var item = new SalesOrderItemDO(); item.setId(101L);
        var template = new RegistrationChecklistTemplateDO(); template.setPublishedVersionId(20L);
        var definition = new RegistrationChecklistTemplateItemDO(); definition.setId(30L); definition.setEnabled(true);
        definition.setItemType("checkbox"); definition.setItemKey("verify"); definition.setTitle("核对报名资料");
        var cases = new ArrayList<RegistrationCaseDO>();
        when(orderMapper.selectByIdForUpdate(10L, 1L)).thenReturn(order);
        when(orderMapper.selectById(10L)).thenReturn(order);
        when(orderItemMapper.selectListByOrderId(10L)).thenReturn(List.of(item));
        when(caseMapper.selectListByOrderId(10L)).thenReturn(List.of());
        when(templateMapper.selectCurrent()).thenReturn(template);
        when(templateItemMapper.selectByVersionId(20L)).thenReturn(List.of(definition));
        when(caseMapper.insert(any(RegistrationCaseDO.class))).thenAnswer(call -> {
            RegistrationCaseDO row = call.getArgument(0); row.setId(1000L); cases.add(row); return 1;
        });

        service.ensureCaseAfterRegistrationApproval(10L, LocalDateTime.now());

        assertEquals(expectedMode, cases.getFirst().getAssignmentMode());
    }

    @Test
    void completingAutoClassCaseNeverEstablishesService() {
        var registrationCase = editableCase();
        var relation = new ServiceRelationDO().setId(900L).setOrderId(10L).setOrderItemId(301L).setPersonId(501L).setStatus("active");
        var orderItem = new SalesOrderItemDO(); orderItem.setId(301L);
        stubCompletable(registrationCase);
        when(serviceRelationMapper.selectByCaseForUpdate(1L, 1L)).thenReturn(List.of(relation));
        when(orderItemMapper.selectListByOrderIdForUpdate(10L, 1L)).thenReturn(List.of(orderItem));

        service.complete(1L, 9L, request());

        verify(serviceRelationMapper, never()).insert(any(ServiceRelationDO.class));
        verifyNoInteractions(unscheduledClasses);
        assertEquals("completed", registrationCase.getStatus());
    }

    @Test
    void completingAutoClassCaseWithoutEffectiveEnrollmentIsRejected() {
        var registrationCase = editableCase();
        stubCompletable(registrationCase);
        when(serviceRelationMapper.selectByCaseForUpdate(1L, 1L)).thenReturn(List.of());

        var error = assertThrows(ServiceException.class, () -> service.complete(1L, 9L, request()));

        assertEquals(REGISTRATION_DELIVERY_INVALID.getCode(), error.getCode());
        verify(serviceRelationMapper, never()).insert(any(ServiceRelationDO.class));
        verifyNoInteractions(unscheduledClasses);
    }

    private void stubCompletable(RegistrationCaseDO registrationCase) {
        var form = new cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.StudentInfoFormDO();
        form.setSubmitSource("reuse");
        when(studentInfoTaskService.formForRegistration(registrationCase)).thenReturn(form);
        var order = new SalesOrderDO().setId(10L).setStatus(STATUS_EFFECTIVE).setPersonId(501L).setAgreedExamMonth("2027-03");
        var checklist = new RegistrationCaseChecklistItemDO(); checklist.setId(101L); checklist.setItemType("checkbox"); checklist.setChecked(true);
        when(caseMapper.selectById(1L)).thenReturn(registrationCase);
        when(caseMapper.selectByIdForUpdate(1L, 1L)).thenReturn(registrationCase);
        when(orderMapper.selectByIdForUpdate(10L, 1L)).thenReturn(order);
        when(caseItemMapper.selectByCaseId(1L)).thenReturn(List.of(checklist));
    }

    private static RegistrationCaseDO editableCase() {
        var registrationCase = new RegistrationCaseDO();
        registrationCase.setId(1L); registrationCase.setOrderId(10L); registrationCase.setOrderItemId(301L);
        registrationCase.setStatus("pending"); registrationCase.setVersion(0);
        registrationCase.setAssignmentMode(AutoClassEnrollmentService.ASSIGNMENT_MODE);
        return registrationCase;
    }

    private static RegistrationVersionReqVO request() {
        var request = new RegistrationVersionReqVO(); request.setVersion(0); request.setIdempotencyKey("complete-auto");
        return request;
    }
}
