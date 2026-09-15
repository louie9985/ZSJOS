package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.RegistrationCaseRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.RegistrationCloseReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.RegistrationVersionReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.RegistrationPlannerUpdateReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderItemDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.DeliveryClassDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.product.ZsjosProductCategoryDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.product.ZsjosProductDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.RegistrationCaseChecklistItemDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.RegistrationCaseDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.RegistrationCommandDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.RegistrationItemAttachmentDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.RegistrationCaseRouteDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.RegistrationClassAssignmentDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
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
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.RegistrationClassAssignmentMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.DeliveryClassMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.product.ZsjosProductCategoryMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.product.ZsjosProductMapper;
import cn.iocoder.yudao.module.zsjos.service.deliveryclass.DeliveryClassService;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static cn.iocoder.yudao.module.zsjos.service.registration.RegistrationConstants.STATUS_PENDING;
import static cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.STATUS_PENDING_APPROVAL;
import static cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.STATUS_EFFECTIVE;
import static cn.iocoder.yudao.module.zsjos.service.registration.RegistrationConstants.STATUS_CANCELLED;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.REGISTRATION_FINANCE_PENDING;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.REGISTRATION_STATE_INVALID;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.REGISTRATION_ROUTE_INVALID;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.REGISTRATION_ATTACHMENT_REQUIRED;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.REGISTRATION_IDEMPOTENCY_RESULT_INVALID;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.DELIVERY_CLASS_USER_INVALID;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.REGISTRATION_CLASS_ASSIGNMENT_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;

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
    @Mock private RegistrationClassAssignmentMapper classAssignmentMapper;
    @Mock private DeliveryClassMapper deliveryClassMapper;
    @Mock private DeliveryClassService deliveryClassService;
    @Mock private ZsjosProductCategoryMapper productCategoryMapper;
    @Mock private ZsjosProductMapper productMapper;
    @Mock private SalesOrderItemMapper orderItemMapper;
    @Mock private PersonMapper personMapper;
    @Mock private LeadAssignmentRelationMapper userRelationMapper;
    @Mock private FileApi fileApi;
    @Mock private RoleApi roleApi;
    @Mock private PermissionApi permissionApi;
    @Mock private AdminUserApi adminUserApi;
    @Mock private DeptApi deptApi;
    @Mock private RegistrationNotifyPublisher registrationNotifyPublisher;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @org.junit.jupiter.params.provider.ValueSource(strings = {"broken", "{}", "{\"name\":\"旧名称\",\"selectedAttrValuesJson\":\"broken\"}"})
    void classAssignmentNeverUsesCurrentProductNameAsHistory(String snapshot) {
        var registrationCase = new RegistrationCaseDO(); registrationCase.setId(1L); registrationCase.setOrderId(10L);
        registrationCase.setStatus(STATUS_PENDING); registrationCase.setAssignmentMode("class_per_item");
        var order = new SalesOrderDO(); order.setId(10L); order.setStatus(STATUS_PENDING_APPROVAL);
        var item = new SalesOrderItemDO(); item.setId(100L); item.setProductId(200L); item.setProductSnapshot(snapshot);
        var product = new ZsjosProductDO(); product.setId(200L); product.setName("今日改名"); product.setCategoryId(300L);
        when(caseMapper.selectById(1L)).thenReturn(registrationCase);
        when(orderMapper.selectById(10L)).thenReturn(order);
        when(orderItemMapper.selectListByOrderId(10L)).thenReturn(List.of(item));
        when(productMapper.selectById(200L)).thenReturn(product);
        var result = service.getCase(1L).getClassAssignments().getFirst();
        assertEquals(snapshot != null && snapshot.contains("旧名称") ? "旧名称" : "历史标签缺失", result.getProductName());
        assertEquals(300L, result.getCategoryId());
    }

    /** Orders persist only product_ref, so the category must resolve without a product_id. */
    @Test
    void classAssignmentResolvesCategoryByProductRefWhenIdMissing() {
        var item = new SalesOrderItemDO(); item.setId(100L); item.setProductRef("SPU-1");
        item.setProductSnapshot("{\"name\":\"师徒班\",\"categoryId\":999}");
        var product = new ZsjosProductDO(); product.setId(200L); product.setCategoryId(300L);
        stubClassAssignmentCase(item);
        when(productMapper.selectByProductRef("SPU-1")).thenReturn(product);

        var result = service.getCase(1L).getClassAssignments().getFirst();

        assertEquals(300L, result.getCategoryId());
    }

    @Test
    void classAssignmentFallsBackToSnapshotCategoryWhenProductDeleted() {
        var item = new SalesOrderItemDO(); item.setId(100L); item.setProductRef("SPU-1");
        item.setProductSnapshot(cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(
                cn.iocoder.yudao.module.zsjos.service.lead.product.LeadProductSnapshot.of("SPU-1", "师徒班",
                        List.of(new cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.ZsjosProductCategoryPathNodeVO(7L, "一级"),
                                new cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.ZsjosProductCategoryPathNodeVO(300L, "师徒班")))));
        stubClassAssignmentCase(item);
        when(productMapper.selectByProductRef("SPU-1")).thenReturn(null);

        var result = service.getCase(1L).getClassAssignments().getFirst();

        assertEquals(300L, result.getCategoryId());
        assertEquals("师徒班", result.getProductName());
    }

    private void stubClassAssignmentCase(SalesOrderItemDO item) {
        var registrationCase = new RegistrationCaseDO(); registrationCase.setId(1L); registrationCase.setOrderId(10L);
        registrationCase.setStatus(STATUS_PENDING); registrationCase.setAssignmentMode("class_per_item");
        var order = new SalesOrderDO(); order.setId(10L); order.setStatus(STATUS_PENDING_APPROVAL);
        when(caseMapper.selectById(1L)).thenReturn(registrationCase);
        when(orderMapper.selectById(10L)).thenReturn(order);
        when(orderItemMapper.selectListByOrderId(10L)).thenReturn(List.of(item));
    }

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
        when(orderMapper.selectByIdForUpdate(10L, 1L)).thenReturn(order);
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
        when(orderMapper.selectByIdForUpdate(10L, 1L)).thenReturn(order);
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
        when(orderMapper.selectByIdForUpdate(10L, 1L)).thenReturn(order);
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
        when(orderMapper.selectByIdForUpdate(10L, 1L)).thenReturn(order);
        when(caseItemMapper.selectByCaseId(1L)).thenReturn(List.of(item));
        when(caseRouteMapper.selectByCaseId(1L)).thenReturn(List.of(route));
        when(userRelationMapper.selectListBySourceUserIds(any(), any())).thenReturn(List.of(assignment));
        when(adminUserApi.getUserList(any())).thenReturn(List.of(planner));
        when(orderItemMapper.selectListByOrderIdForUpdate(10L, 1L)).thenReturn(List.of(first, second));
        when(deliveryClassMapper.selectPending()).thenReturn(deliveryClass(299L, true, null, null));
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

    @Test
    void classModeCompletesEachOrderItemIntoItsSelectedClass() {
        RegistrationCaseDO registrationCase = editableCase();
        registrationCase.setAssignmentMode("class_per_item");
        SalesOrderDO order = new SalesOrderDO();
        order.setId(10L); order.setStatus(STATUS_EFFECTIVE); order.setPersonId(501L);
        RegistrationCaseChecklistItemDO checklist = new RegistrationCaseChecklistItemDO();
        checklist.setId(101L); checklist.setItemType("checkbox"); checklist.setChecked(true);
        SalesOrderItemDO formalItem = new SalesOrderItemDO();
        formalItem.setId(301L); formalItem.setProductId(401L); formalItem.setProductSnapshot("正式班商品");
        SalesOrderItemDO pendingItem = new SalesOrderItemDO();
        pendingItem.setId(302L); pendingItem.setProductId(402L); pendingItem.setProductSnapshot("待分班商品");
        RegistrationClassAssignmentDO formalAssignment = assignment(301L, 202L);
        RegistrationClassAssignmentDO pendingAssignment = assignment(302L, 201L);
        DeliveryClassDO formalClass = deliveryClass(202L, false, 81L, 31L);
        DeliveryClassDO pendingClass = deliveryClass(201L, true, null, null);
        PersonDO person = new PersonDO(); person.setId(501L); person.setVersion(0); person.setPersonNo("XY202609080001");
        when(caseMapper.selectByIdForUpdate(1L, 1L)).thenReturn(registrationCase);
        when(orderMapper.selectByIdForUpdate(10L, 1L)).thenReturn(order);
        when(caseItemMapper.selectByCaseId(1L)).thenReturn(List.of(checklist));
        when(orderItemMapper.selectListByOrderIdForUpdate(10L, 1L)).thenReturn(List.of(formalItem, pendingItem));
        when(classAssignmentMapper.selectByCaseIdForUpdate(1L, 1L))
                .thenReturn(List.of(formalAssignment, pendingAssignment));
        when(productMapper.selectById(401L)).thenReturn(new ZsjosProductDO().setCategoryId(81L));
        when(productMapper.selectById(402L)).thenReturn(new ZsjosProductDO().setCategoryId(82L));
        when(deliveryClassMapper.selectByIdForUpdate(201L, 1L)).thenReturn(pendingClass);
        when(deliveryClassMapper.selectByIdForUpdate(202L, 1L)).thenReturn(formalClass);
        when(deliveryClassService.validateHomeroom(31L))
                .thenReturn(new AdminUserRespDTO().setId(31L).setStatus(0));
        when(productCategoryMapper.selectById(82L))
                .thenReturn(new ZsjosProductCategoryDO().setId(82L).setName("职业资格").setParentId(0L));
        when(personMapper.selectByIdForUpdate(501L, 1L)).thenReturn(person);
        RegistrationVersionReqVO request = new RegistrationVersionReqVO();
        request.setVersion(0); request.setIdempotencyKey("complete-class-mode");

        cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(1L);
        try {
            service.complete(1L, 9L, request);
        } finally {
            cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear();
        }

        ArgumentCaptor<ServiceRelationDO> relationCaptor = ArgumentCaptor.forClass(ServiceRelationDO.class);
        verify(serviceRelationMapper, times(2)).insert(relationCaptor.capture());
        List<ServiceRelationDO> relations = relationCaptor.getAllValues();
        assertEquals(202L, relations.get(0).getClassId());
        assertEquals(31L, relations.get(0).getOwnerUserId());
        assertEquals(201L, relations.get(1).getClassId());
        assertNull(relations.get(1).getOwnerUserId());
        verify(registrationNotifyPublisher).publishPlannerAssigned(
                registrationCase, order, "XY202609080001", 31L, 501L);
        verify(orderMapper).selectByIdForUpdate(10L, 1L);
        verify(orderItemMapper).selectListByOrderIdForUpdate(10L, 1L);
        InOrder classLockOrder = inOrder(deliveryClassMapper);
        classLockOrder.verify(deliveryClassMapper).selectByIdForUpdate(201L, 1L);
        classLockOrder.verify(deliveryClassMapper).selectByIdForUpdate(202L, 1L);
        verify(orderItemMapper, never()).selectListByOrderId(10L);
    }

    @Test
    void classModeRejectsHomeroomWithoutPlannerCapabilities() {
        RegistrationCaseDO registrationCase = editableCase();
        registrationCase.setAssignmentMode("class_per_item");
        SalesOrderDO order = new SalesOrderDO().setId(10L).setStatus(STATUS_EFFECTIVE).setPersonId(501L);
        RegistrationCaseChecklistItemDO checklist = new RegistrationCaseChecklistItemDO();
        checklist.setId(101L); checklist.setItemType("checkbox"); checklist.setChecked(true);
        SalesOrderItemDO orderItem = new SalesOrderItemDO().setId(301L).setProductId(401L);
        when(caseMapper.selectByIdForUpdate(1L, 1L)).thenReturn(registrationCase);
        when(orderMapper.selectByIdForUpdate(10L, 1L)).thenReturn(order);
        when(caseItemMapper.selectByCaseId(1L)).thenReturn(List.of(checklist));
        when(orderItemMapper.selectListByOrderIdForUpdate(10L, 1L)).thenReturn(List.of(orderItem));
        when(classAssignmentMapper.selectByCaseIdForUpdate(1L, 1L)).thenReturn(List.of(assignment(301L, 201L)));
        when(productMapper.selectById(401L)).thenReturn(new ZsjosProductDO().setCategoryId(81L));
        when(deliveryClassMapper.selectByIdForUpdate(201L, 1L))
                .thenReturn(deliveryClass(201L, false, 81L, 31L));
        when(deliveryClassService.validateHomeroom(31L)).thenThrow(new ServiceException(DELIVERY_CLASS_USER_INVALID));
        RegistrationVersionReqVO request = new RegistrationVersionReqVO();
        request.setVersion(0); request.setIdempotencyKey("complete-ineligible-homeroom");

        TenantContextHolder.setTenantId(1L);
        try {
            ServiceException error = assertThrows(ServiceException.class, () -> service.complete(1L, 9L, request));
            assertEquals(REGISTRATION_CLASS_ASSIGNMENT_INVALID.getCode(), error.getCode());
        } finally {
            TenantContextHolder.clear();
        }
        verifyNoInteractions(serviceRelationMapper);
        verifyNoInteractions(registrationNotifyPublisher);
    }

    @Test
    void closeMarksRegistrationCancelledWithReason() {
        RegistrationCaseDO registrationCase = editableCase();
        when(caseMapper.selectByIdForUpdate(1L, 1L)).thenReturn(registrationCase);
        RegistrationCloseReqVO request = new RegistrationCloseReqVO();
        request.setVersion(0); request.setIdempotencyKey("close-registration"); request.setReason("无需服务");

        cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(1L);
        try {
            service.close(1L, 9L, request);
        } finally {
            cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear();
        }

        assertEquals(STATUS_CANCELLED, registrationCase.getStatus());
        assertNotNull(registrationCase.getCancelledAt());
        assertEquals("无需服务", registrationCase.getCancelReason());
        verify(caseMapper).updateById(registrationCase);
    }

    @Test
    void closeRejectsCompletedRegistration() {
        RegistrationCaseDO registrationCase = editableCase();
        registrationCase.setStatus(RegistrationConstants.STATUS_COMPLETED);
        when(caseMapper.selectByIdForUpdate(1L, 1L)).thenReturn(registrationCase);
        RegistrationCloseReqVO request = new RegistrationCloseReqVO();
        request.setVersion(0); request.setIdempotencyKey("close-completed"); request.setReason("无需服务");

        cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(1L);
        try {
            ServiceException error = assertThrows(ServiceException.class, () -> service.close(1L, 9L, request));
            assertEquals(REGISTRATION_STATE_INVALID.getCode(), error.getCode());
        } finally {
            cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear();
        }
    }

    private RegistrationCaseDO editableCase() {
        RegistrationCaseDO registrationCase = new RegistrationCaseDO();
        registrationCase.setId(1L); registrationCase.setOrderId(10L);
        registrationCase.setStatus(STATUS_PENDING); registrationCase.setVersion(0);
        return registrationCase;
    }

    private static RegistrationClassAssignmentDO assignment(Long orderItemId, Long classId) {
        RegistrationClassAssignmentDO assignment = new RegistrationClassAssignmentDO();
        assignment.setOrderItemId(orderItemId); assignment.setClassId(classId);
        return assignment;
    }

    private static DeliveryClassDO deliveryClass(Long id, boolean systemClass, Long categoryId, Long ownerId) {
        DeliveryClassDO deliveryClass = new DeliveryClassDO();
        deliveryClass.setId(id); deliveryClass.setSystemClass(systemClass); deliveryClass.setCategoryId(categoryId);
        deliveryClass.setHomeroomUserId(ownerId); deliveryClass.setHomeroomUserNameSnapshot(ownerId == null ? null : "班主任");
        deliveryClass.setClassNo(systemClass ? "PENDING" : "BJ202609080000000001");
        deliveryClass.setClassName(systemClass ? "待分班" : "正式班"); deliveryClass.setStatus("SERVING");
        return deliveryClass;
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
