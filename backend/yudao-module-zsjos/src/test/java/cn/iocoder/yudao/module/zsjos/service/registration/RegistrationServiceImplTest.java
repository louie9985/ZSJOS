package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.RegistrationCaseRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.RegistrationVersionReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.RegistrationPlannerUpdateReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderItemDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.RegistrationCaseChecklistItemDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.RegistrationCaseDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.RegistrationCommandDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.RegistrationItemAttachmentDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.RegistrationCaseRouteDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderItemMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAssignmentRelationMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.RegistrationCaseChecklistItemMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.RegistrationCaseMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.RegistrationCommandMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.RegistrationCaseRouteMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.RegistrationItemAttachmentMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.RegistrationItemMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.system.api.permission.RoleApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.permission.dto.RoleRespDTO;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static cn.iocoder.yudao.module.zsjos.service.registration.RegistrationConstants.STATUS_PENDING;
import static cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.STATUS_PENDING_APPROVAL;
import static cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.STATUS_EFFECTIVE;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.REGISTRATION_FINANCE_PENDING;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.REGISTRATION_ROUTE_INVALID;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.REGISTRATION_ATTACHMENT_REQUIRED;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.REGISTRATION_IDEMPOTENCY_RESULT_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceImplTest {

    @InjectMocks private RegistrationServiceImpl service;
    @Mock private RegistrationCaseMapper caseMapper;
    @Mock private RegistrationCaseChecklistItemMapper caseItemMapper;
    @Mock private SalesOrderMapper orderMapper;
    @Mock private RegistrationCommandMapper commandMapper;
    @Mock private RegistrationCaseRouteMapper caseRouteMapper;
    @Mock private RegistrationItemAttachmentMapper attachmentMapper;
    @Mock private RegistrationItemMapper registrationItemMapper;
    @Mock private ServiceRelationMapper serviceRelationMapper;
    @Mock private SalesOrderItemMapper orderItemMapper;
    @Mock private PersonMapper personMapper;
    @Mock private LeadAssignmentRelationMapper userRelationMapper;
    @Mock private FileApi fileApi;
    @Mock private RoleApi roleApi;
    @Mock private PermissionApi permissionApi;
    @Mock private AdminUserApi adminUserApi;
    @Mock private DeptApi deptApi;
    @Mock private RegistrationNotifyPublisher registrationNotifyPublisher;

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

    @Test
    void completeRequiresAtLeastOneRoute() {
        RegistrationCaseDO registrationCase = editableCase();
        SalesOrderDO order = new SalesOrderDO(); order.setId(10L); order.setStatus(STATUS_EFFECTIVE);
        RegistrationCaseChecklistItemDO item = new RegistrationCaseChecklistItemDO();
        item.setId(101L); item.setItemType("checkbox"); item.setChecked(true);
        when(caseMapper.selectByIdForUpdate(1L, 1L)).thenReturn(registrationCase);
        when(orderMapper.selectById(10L)).thenReturn(order);
        when(caseItemMapper.selectByCaseId(1L)).thenReturn(List.of(item));

        ServiceException error = completeAndCapture("route-required");

        assertEquals(REGISTRATION_ROUTE_INVALID.getCode(), error.getCode());
    }

    @Test
    void completeRequiresFilesForRequiredAttachmentItem() {
        RegistrationCaseDO registrationCase = editableCase();
        SalesOrderDO order = new SalesOrderDO(); order.setId(10L); order.setStatus(STATUS_EFFECTIVE);
        RegistrationCaseChecklistItemDO item = new RegistrationCaseChecklistItemDO();
        item.setId(102L); item.setItemType("attachment"); item.setAttachmentRequired(true); item.setChecked(false);
        when(caseMapper.selectByIdForUpdate(1L, 1L)).thenReturn(registrationCase);
        when(orderMapper.selectById(10L)).thenReturn(order);
        when(caseItemMapper.selectByCaseId(1L)).thenReturn(List.of(item));

        ServiceException error = completeAndCapture("attachment-required");

        assertEquals(REGISTRATION_ATTACHMENT_REQUIRED.getCode(), error.getCode());
    }

    @Test
    void attachmentReplayReturnsOnlyPersistedResultAttachment() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "same.pdf", "application/pdf", "%PDF-1.4".getBytes());
        RegistrationCommandDO command = new RegistrationCommandDO();
        command.setRegistrationCaseId(1L); command.setCommandType("upload-attachment");
        command.setRequestFingerprint("102:same.pdf:" + file.getSize()); command.setResultAttachmentId(99L);
        RegistrationItemAttachmentDO attachment = new RegistrationItemAttachmentDO();
        attachment.setId(99L); attachment.setRegistrationCaseId(1L); attachment.setChecklistItemId(102L);
        RegistrationCaseDO registrationCase = editableCase(); registrationCase.setVersion(4);
        when(commandMapper.insert(any(RegistrationCommandDO.class))).thenThrow(new DuplicateKeyException("duplicate"));
        when(commandMapper.selectByIdempotencyKey("upload-replay")).thenReturn(command);
        when(attachmentMapper.selectById(99L)).thenReturn(attachment);
        when(caseMapper.selectById(1L)).thenReturn(registrationCase);

        var result = service.uploadAttachment(1L, 102L, 9L, 0, "upload-replay", file);

        assertEquals(99L, result.getId());
        assertEquals(4, result.getVersion());
        verify(attachmentMapper, never()).selectByItemId(102L);
    }

    @Test
    void attachmentReplayRejectsMissingPersistedResult() {
        MockMultipartFile file = new MockMultipartFile("file", "same.pdf", "application/pdf", "%PDF-1.4".getBytes());
        RegistrationCommandDO command = new RegistrationCommandDO();
        command.setRegistrationCaseId(1L); command.setCommandType("upload-attachment");
        command.setRequestFingerprint("102:same.pdf:" + file.getSize()); command.setResultAttachmentId(99L);
        when(commandMapper.insert(any(RegistrationCommandDO.class))).thenThrow(new DuplicateKeyException("duplicate"));
        when(commandMapper.selectByIdempotencyKey("missing-result")).thenReturn(command);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.uploadAttachment(1L, 102L, 9L, 0, "missing-result", file));

        assertEquals(REGISTRATION_IDEMPOTENCY_RESULT_INVALID.getCode(), error.getCode());
    }

    @Test
    void attachmentPersistenceFailureDeletesStoredInfraFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "proof.png", "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
        RegistrationCaseDO registrationCase = editableCase();
        RegistrationCaseChecklistItemDO item = new RegistrationCaseChecklistItemDO();
        item.setId(102L); item.setRegistrationCaseId(1L); item.setItemType("attachment"); item.setVersion(0);
        FileInfoRespDTO stored = new FileInfoRespDTO(); stored.setId(88L); stored.setName("proof.png");
        stored.setType("image/png"); stored.setSize(file.getSize()); stored.setUrl("https://files/proof.png");
        when(caseMapper.selectByIdForUpdate(1L, 1L)).thenReturn(registrationCase);
        when(caseItemMapper.selectById(102L)).thenReturn(item);
        when(attachmentMapper.selectByItemId(102L)).thenReturn(List.of());
        when(fileApi.createFileInfo(any(), any(), any(), any())).thenReturn(stored);
        when(attachmentMapper.insert(any(RegistrationItemAttachmentDO.class))).thenThrow(new IllegalStateException("db failed"));

        cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(1L);
        try {
            assertThrows(IllegalStateException.class,
                    () -> service.uploadAttachment(1L, 102L, 9L, 0, "upload-failure", file));
        } finally {
            cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear();
        }

        verify(fileApi).deleteFileIfExists(88L);
    }

    @Test
    void strictMimeValidationRejectsZipAndOctetStreamForOfficeExtensions() {
        assertFalse((Boolean) org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                service, "validAttachmentType", "proof.docx", "application/zip"));
        assertFalse((Boolean) org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                service, "validAttachmentType", "proof.xlsx", "application/octet-stream"));
        assertFalse((Boolean) org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                service, "validAttachmentType", "proof.pdf", "image/png"));
    }

    @Test
    void unchangedStudyPlannerDoesNotPublishAnotherAssignment() {
        RegistrationCaseDO registrationCase = editableCase(); registrationCase.setStudyPlannerUserId(30L);
        RegistrationCaseRouteDO route = new RegistrationCaseRouteDO();
        route.setId(201L); route.setRegistrationCaseId(1L); route.setAssigneeType("study_planner");
        route.setDepartmentId(40L); route.setVersion(0);
        RegistrationCaseChecklistItemDO plannerItem = new RegistrationCaseChecklistItemDO();
        plannerItem.setId(202L); plannerItem.setItemType("study_planner"); plannerItem.setVersion(0);
        AdminUserRespDTO planner = new AdminUserRespDTO();
        planner.setId(30L); planner.setDeptId(40L); planner.setStatus(0); planner.setNickname("规划师");
        when(caseMapper.selectByIdForUpdate(1L, 1L)).thenReturn(registrationCase);
        when(caseRouteMapper.selectByCaseId(1L)).thenReturn(List.of(route));
        when(caseItemMapper.selectByCaseId(1L)).thenReturn(List.of(plannerItem));
        when(adminUserApi.getUser(30L)).thenReturn(planner);
        RegistrationPlannerUpdateReqVO request = new RegistrationPlannerUpdateReqVO();
        request.setVersion(0); request.setIdempotencyKey("same-planner"); request.setStudyPlannerUserId(30L);
        cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(1L);
        try {
            service.updateStudyPlanner(1L, 9L, request);
        } finally {
            cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear();
        }

        verify(registrationNotifyPublisher, never()).publishPlannerAssigned(any(), any(), any(), any(), any());
    }

    @Test
    void completeCreatesEveryServiceAndPublishesOneStudentNotification() {
        RegistrationCaseDO registrationCase = editableCase();
        SalesOrderDO order = new SalesOrderDO();
        order.setId(10L); order.setStatus(STATUS_EFFECTIVE); order.setPersonId(501L);
        RegistrationCaseChecklistItemDO item = new RegistrationCaseChecklistItemDO();
        item.setId(101L); item.setItemType("checkbox"); item.setChecked(true);
        RegistrationCaseRouteDO route = new RegistrationCaseRouteDO();
        route.setId(201L); route.setAssigneeType("study_planner"); route.setSelected(true);
        route.setAssigneeUserId(30L);
        LeadAssignmentRelationDO assignment = new LeadAssignmentRelationDO();
        assignment.setTargetUserId(30L); assignment.setStatus(0);
        AdminUserRespDTO planner = new AdminUserRespDTO();
        planner.setId(30L); planner.setStatus(0); planner.setNickname("规划师");
        SalesOrderItemDO first = new SalesOrderItemDO(); first.setId(301L);
        SalesOrderItemDO second = new SalesOrderItemDO(); second.setId(302L);
        PersonDO person = new PersonDO(); person.setId(501L); person.setVersion(0);
        when(caseMapper.selectByIdForUpdate(1L, 1L)).thenReturn(registrationCase);
        when(orderMapper.selectById(10L)).thenReturn(order);
        when(caseItemMapper.selectByCaseId(1L)).thenReturn(List.of(item));
        when(caseRouteMapper.selectByCaseId(1L)).thenReturn(List.of(route));
        when(userRelationMapper.selectListBySourceUserIds(any(), any())).thenReturn(List.of(assignment));
        when(adminUserApi.getUserList(any())).thenReturn(List.of(planner));
        when(orderItemMapper.selectListByOrderId(10L)).thenReturn(List.of(first, second));
        when(personMapper.selectByIdForUpdate(501L, 1L)).thenReturn(person);
        RegistrationVersionReqVO request = new RegistrationVersionReqVO();
        request.setVersion(0); request.setIdempotencyKey("complete-student");

        cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(1L);
        try {
            service.complete(1L, 9L, request);
        } finally {
            cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear();
        }

        verify(serviceRelationMapper, times(2)).insert(any(cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO.class));
        verify(registrationNotifyPublisher).publishPlannerAssigned(registrationCase, order, null, 30L, 501L);
    }

    private RegistrationCaseDO editableCase() {
        RegistrationCaseDO registrationCase = new RegistrationCaseDO();
        registrationCase.setId(1L); registrationCase.setOrderId(10L);
        registrationCase.setStatus(STATUS_PENDING); registrationCase.setVersion(0);
        return registrationCase;
    }

    private ServiceException completeAndCapture(String idempotencyKey) {
        RegistrationVersionReqVO request = new RegistrationVersionReqVO();
        request.setVersion(0); request.setIdempotencyKey(idempotencyKey);
        cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(1L);
        try {
            return assertThrows(ServiceException.class, () -> service.complete(1L, 9L, request));
        } finally {
            cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear();
        }
    }
}
