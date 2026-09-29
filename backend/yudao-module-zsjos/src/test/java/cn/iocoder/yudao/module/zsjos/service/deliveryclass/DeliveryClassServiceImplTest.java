package cn.iocoder.yudao.module.zsjos.service.deliveryclass;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.permission.RoleApi;
import cn.iocoder.yudao.module.system.api.permission.dto.RoleRespDTO;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.deliveryclass.vo.DeliveryClassDirectTransferReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.deliveryclass.vo.DeliveryClassSaveReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.DeliveryClassDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcalendar.ExamScheduleDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.product.ZsjosProductCategoryDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.DeliveryClassMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcalendar.ExamScheduleMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.product.ZsjosProductCategoryMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskCommandService;
import cn.iocoder.yudao.module.zsjos.service.product.ZsjosProductSkuService;
import cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.ExamProductScopeRespVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.DELIVERY_CLASS_CATEGORY_INVALID;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.DELIVERY_CLASS_CATEGORY_LOCKED;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.DELIVERY_CLASS_TRANSFER_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryClassServiceImplTest {
    @InjectMocks private DeliveryClassServiceImpl service;
    @Mock private DeliveryClassMapper classMapper;
    @Mock private ServiceRelationMapper relationMapper;
    @Mock private DeliveryClassScopeService scopeService;
    @Mock private AdminUserApi adminUserApi;
    @Mock private PermissionApi permissionApi;
    @Mock private cn.iocoder.yudao.module.zsjos.service.common.BusinessReadScopeService readScopeService;
    @Mock private RoleApi roleApi;
    @Mock private ZsjosProductSkuService productSkuService;
    @Mock private DeptApi deptApi;
    @Mock private ZsjosProductCategoryMapper categoryMapper;
    @Mock private ExamScheduleMapper scheduleMapper;
    @Mock private BusinessTaskCommandService taskCommandService;
    @Mock private DeliveryClassNotifyPublisher notifyPublisher;
    @Mock private DeliveryClassNumberService numberService;

    @Test void administratorReadScopesDoNotReuseCommandDepartmentScope() {
        var request = new cn.iocoder.yudao.module.zsjos.controller.admin.deliveryclass.vo.DeliveryClassPageReqVO();
        when(permissionApi.hasTenantReadAllAccess(9L)).thenReturn(true);
        when(classMapper.selectDeliveryClassPage(request, java.util.Set.of(), null, true, true))
                .thenReturn(cn.iocoder.yudao.framework.common.pojo.PageResult.empty());
        assertEquals(0L, service.getManagedPage(9L, request).getTotal());
        verifyNoInteractions(scopeService, taskCommandService);
        request.setReadScope("ALL");
        when(readScopeService.resolve("ALL", null, 9L)).thenReturn(null);
        assertEquals(0L, service.getMyPage(9L, request).getTotal());
    }

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void pendingTransferSucceedsAcrossProductCategoriesAndPreservesServiceState() {
        plannerRole(20L);
        ServiceRelationDO relation = relation(10L, 100L, 3);
        DeliveryClassDO pending = deliveryClass(100L, true, null, null);
        DeliveryClassDO target = deliveryClass(200L, false, 8L, 20L);
        when(relationMapper.selectByIdForUpdate(10L, 1L)).thenReturn(relation);
        when(classMapper.selectByIdForUpdate(200L, 1L)).thenReturn(target);
        when(classMapper.selectById(100L)).thenReturn(pending);
        when(adminUserApi.getUser(20L)).thenReturn(enabledUser(20L));
        when(scopeService.contains(9L, 80L)).thenReturn(true);
        when(relationMapper.transferClass(10L, 200L, 20L, 3)).thenReturn(1);

        service.directTransfer(10L, request(200L, 3), 9L);

        verify(relationMapper).transferClass(10L, 200L, 20L, 3);
        verify(taskCommandService).reassignPending(anyCollection(), eq(10L), eq(20L));
        verify(notifyPublisher).publishOwnerChanged("direct-transfer:10:v3", 10L, 200L, 11L, 20L, "业务调班");
        assertEquals("accepted", relation.getAcceptanceStatus());
    }

    @Test
    void directTransferRejectsSystemClassTarget() {
        ServiceRelationDO relation = relation(10L, 100L, 3);
        when(relationMapper.selectByIdForUpdate(10L, 1L)).thenReturn(relation);
        when(classMapper.selectByIdForUpdate(200L, 1L)).thenReturn(deliveryClass(200L, true, null, 20L));

        var error = assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.directTransfer(10L, request(200L, 3), 9L));

        assertEquals(DELIVERY_CLASS_TRANSFER_INVALID.getCode(), error.getCode());
        verify(relationMapper, never()).transferClass(anyLong(), anyLong(), anyLong(), anyInt());
        verifyNoInteractions(taskCommandService, notifyPublisher);
    }

    @Test
    void homeroomChangePublishesOneIdempotentEventPerServiceRelation() {
        plannerRole(20L);
        DeliveryClassDO current = deliveryClass(100L, false, 8L, 11L);
        current.setClassName("原班级"); current.setExamScheduleId(70L); current.setVersion(2);
        ServiceRelationDO first = relation(10L, 100L, 3);
        ServiceRelationDO second = relation(12L, 100L, 5);
        ZsjosProductCategoryDO category = new ZsjosProductCategoryDO();
        category.setId(8L); category.setName("职业资格"); category.setParentId(0L);
        category.setStatus(CommonStatusEnum.ENABLE.getStatus());
        ExamScheduleDO schedule = new ExamScheduleDO();
        schedule.setId(70L); schedule.setCategoryId(8L); schedule.setRecordStatus("PUBLISHED");
        schedule.setScheduleType("EXACT"); schedule.setExactDate(java.time.LocalDate.of(2026, 12, 1));
        when(classMapper.selectByIdForUpdate(100L, 1L)).thenReturn(current);
        when(adminUserApi.getUser(9L)).thenReturn(enabledUser(9L).setDeptId(80L));
        when(scheduleMapper.selectById(70L)).thenReturn(schedule);
        when(adminUserApi.getUser(20L)).thenReturn(enabledUser(20L).setDeptId(80L).setNickname("新班主任"));
        when(classMapper.updateById(current)).thenReturn(1);
        when(relationMapper.selectClassRelationsForUpdate(100L, 1L)).thenReturn(java.util.List.of(first, second));
        when(relationMapper.transferClass(10L, 100L, 20L, 3)).thenReturn(1);
        when(relationMapper.transferClass(12L, 100L, 20L, 5)).thenReturn(1);
        DeliveryClassSaveReqVO request = new DeliveryClassSaveReqVO();
        request.setClassName("新班级"); request.setExamScheduleId(70L);
        request.setHomeroomUserId(20L); request.setVersion(2);

        service.update(100L, request, 9L);

        verify(notifyPublisher).publishOwnerChanged(
                "homeroom-change:100:relation:10:v3", 10L, 100L, 11L, 20L, "班主任变更");
        verify(notifyPublisher).publishOwnerChanged(
                "homeroom-change:100:relation:12:v5", 12L, 100L, 11L, 20L, "班主任变更");
        verify(taskCommandService, times(2)).reassignPending(anyCollection(), anyLong(), eq(20L));
        verifyNoInteractions(productSkuService, categoryMapper);
        assertEquals("新班级", current.getClassName());
    }





    @Test void createsNamedClassWithoutProductAndRequiresExplicitNameAndExam() {
        plannerRole(20L);
        var schedule = new ExamScheduleDO().setId(70L).setScheduleName("自由考期").setRecordStatus("PUBLISHED")
                .setScheduleType("EXACT").setExactDate(java.time.LocalDate.now().plusDays(10));
        when(scheduleMapper.selectById(70L)).thenReturn(schedule);
        when(adminUserApi.getUser(9L)).thenReturn(enabledUser(9L).setDeptId(80L));
        when(adminUserApi.getUser(20L)).thenReturn(enabledUser(20L).setDeptId(80L));
        when(numberService.next()).thenReturn("BJ209910100001");
        var req = new DeliveryClassSaveReqVO(); req.setClassName("  自由班级  "); req.setExamScheduleId(70L); req.setHomeroomUserId(20L);
        service.create(req, 9L);
        var saved = org.mockito.ArgumentCaptor.forClass(DeliveryClassDO.class);
        verify(classMapper).insert(saved.capture());
        assertEquals("自由班级", saved.getValue().getClassName());
        org.junit.jupiter.api.Assertions.assertNull(saved.getValue().getProductId());
        org.junit.jupiter.api.Assertions.assertNull(saved.getValue().getCategoryId());
        org.junit.jupiter.api.Assertions.assertTrue(saved.getValue().getExamScheduleSnapshot().startsWith("自由考期"));
        verifyNoInteractions(productSkuService, categoryMapper);
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            req.setClassName("  "); req.setExamScheduleId(null);
            var fields = factory.getValidator().validate(req).stream().map(v -> v.getPropertyPath().toString()).toList();
            org.junit.jupiter.api.Assertions.assertTrue(fields.containsAll(java.util.List.of("className", "examScheduleId")));
        }
    }

    @Test void examOptionsIgnoreCatalogAndRejectExpiredDraftOrRevokedRecords() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "exam-options"), ExamScheduleDO.class);
        var future = new ExamScheduleDO().setId(7L).setScheduleName("任意考期").setScheduleType("EXACT")
                .setExactDate(java.time.LocalDate.now().plusDays(10)).setRecordStatus("PUBLISHED");
        var ended = new ExamScheduleDO().setId(8L).setScheduleType("EXACT").setExactDate(java.time.LocalDate.now().minusDays(1));
        when(scheduleMapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(java.util.List.of(future, ended));
        var options = service.examOptions(null, null, null, null);
        assertEquals(1, options.size()); assertEquals(7L, options.getFirst().id());
        org.junit.jupiter.api.Assertions.assertTrue(options.getFirst().displayName().startsWith("任意考期"));
        verifyNoInteractions(productSkuService, categoryMapper);
        var req = new DeliveryClassSaveReqVO(); req.setClassName("手工班级"); req.setExamScheduleId(7L);
        when(scheduleMapper.selectById(7L)).thenReturn(future);
        for (String state : java.util.List.of("DRAFT", "REVOKED")) {
            future.setRecordStatus(state);
            assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class, () -> service.create(req, 9L));
        }
        future.setRecordStatus("PUBLISHED").setExactDate(java.time.LocalDate.now().minusDays(1));
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class, () -> service.create(req, 9L));
        verifyNoInteractions(classMapper, adminUserApi);
    }

    private static ServiceRelationDO relation(Long id, Long classId, Integer version) {
        ServiceRelationDO relation = new ServiceRelationDO();
        relation.setId(id); relation.setTenantId(1L); relation.setClassId(classId); relation.setOrderItemId(300L);
        relation.setOwnerUserId(11L); relation.setAcceptanceStatus("accepted"); relation.setStatus("active");
        relation.setVersion(version);
        return relation;
    }

    private static DeliveryClassDO deliveryClass(Long id, boolean system, Long categoryId, Long ownerId) {
        DeliveryClassDO row = new DeliveryClassDO();
        row.setId(id); row.setTenantId(1L); row.setSystemClass(system); row.setCategoryId(categoryId);
        row.setHomeroomUserId(ownerId); row.setDeptId(system ? null : 80L); row.setStatus("SERVING");
        return row;
    }

    private static AdminUserRespDTO enabledUser(Long id) {
        return new AdminUserRespDTO().setId(id).setStatus(CommonStatusEnum.ENABLE.getStatus());
    }

    private void plannerRole(Long userId) {
        RoleRespDTO role = new RoleRespDTO(); role.setId(3012L); role.setCode("study_planner");
        role.setStatus(CommonStatusEnum.ENABLE.getStatus());
        when(roleApi.getRoleByCode("study_planner")).thenReturn(role);
        when(permissionApi.getEnabledRoleIdsByUserId(userId)).thenReturn(java.util.Set.of(3012L));
    }

    private static ExamProductScopeRespVO scope(Long productId, Long categoryId, Long skuId) {
        return new ExamProductScopeRespVO(productId, "P-500", "产品", categoryId, java.util.List.of(),
                java.util.List.of(), java.util.List.of(), java.util.List.of(
                new ExamProductScopeRespVO.Sku(skuId, "SKU-900", "规格", java.util.Map.of(), java.util.List.of())));
    }

    private static DeliveryClassDirectTransferReqVO request(Long targetId, Integer version) {
        DeliveryClassDirectTransferReqVO req = new DeliveryClassDirectTransferReqVO();
        req.setTargetClassId(targetId); req.setVersion(version); req.setReason("业务调班");
        return req;
    }
}
