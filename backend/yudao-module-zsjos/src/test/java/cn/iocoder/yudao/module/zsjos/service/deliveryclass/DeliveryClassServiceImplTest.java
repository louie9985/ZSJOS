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

    @Test void classStudentNameSurvivesOrderUnlink() {
        var personMapper = mock(cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper.class);
        var orderMapper = mock(cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "personMapper", personMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        var person = new cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO(); person.setName("主档姓名");
        var order = new cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO(); order.setStudentName("订单姓名");
        var relation = new ServiceRelationDO(); relation.setId(1L); relation.setPersonId(2L); relation.setOrderId(3L);
        when(personMapper.selectById(2L)).thenReturn(person); relation.setStudentNameSnapshot("订单姓名");
        cn.iocoder.yudao.module.zsjos.controller.admin.deliveryclass.vo.DeliveryClassStudentRespVO row =
                org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "student", relation);
        assertEquals("订单姓名", row.getStudentName());
        relation.setOrderId(null);
        row = org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "student", relation);
        assertEquals("订单姓名", row.getStudentName());
    }

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "class-date-tests"), DeliveryClassDO.class);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void classStudentReadRestrictsServicesBeforePaginationEvenWhenClassIsReadable() throws Exception {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "class-student-scope"), ServiceRelationDO.class);
        when(permissionApi.hasAnyPermissions(7L, "zsjos:delivery-class:query", DeliveryClassService.PERMISSION_QUERY_MANAGED)).thenReturn(true);
        when(scopeService.resolveRead(7L)).thenReturn(new DeliveryClassScopeService.Scope(false, java.util.Set.of()));
        var query = service.studentReadQuery(91L, 7L);
        String sql = query.getSqlSegment();
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("owner_user_id"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("career_planner_user_id"));
        verifyNoInteractions(adminUserApi);
        if (!"true".equals(System.getenv("ZSJOS_CLASS_MYSQL_VERIFY"))) return;
        String fixture = "WITH zsjos_service_relation AS ("
                + "SELECT 10 id,91 class_id,7 owner_user_id,NULL content_director_user_id,NULL career_planner_user_id,'active' status,'accepted' acceptance_status,NOW() activated_at "
                + "UNION ALL SELECT 11,91,8,NULL,NULL,'active','accepted',NOW() "
                + "UNION ALL SELECT 12,91,8,NULL,7,'active','accepted',NOW() "
                + "UNION ALL SELECT 13,91,8,NULL,7,'paused','accepted',NOW()) SELECT id FROM zsjos_service_relation WHERE ";
        var pattern = java.util.regex.Pattern.compile("#\\{ew.paramNameValuePairs.([A-Za-z0-9]+)}");
        var matcher = pattern.matcher(sql); var bound = new StringBuffer(); var values = new java.util.ArrayList<Object>();
        while(matcher.find()) { values.add(query.getParamNameValuePairs().get(matcher.group(1))); matcher.appendReplacement(bound, "?"); }
        matcher.appendTail(bound);
        try(var db=java.sql.DriverManager.getConnection(System.getenv("ZSJOS_CLASS_MYSQL_URL"),
                System.getenv("ZSJOS_CLASS_MYSQL_USER"),System.getenv("ZSJOS_CLASS_MYSQL_PASSWORD"))) {
            db.setReadOnly(true);
            try(var statement=db.prepareStatement(fixture+bound)) {
                for(int i=0;i<values.size();i++)statement.setObject(i+1,values.get(i));
                try(var result=statement.executeQuery()) {
                    var ids=new java.util.HashSet<Long>(); while(result.next())ids.add(result.getLong(1));
                    assertEquals(java.util.Set.of(10L,12L),ids);
                }
            }
        }
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
        when(classMapper.update(eq(current), any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(1);
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

    @Test
    void metadataEditPreservesExistingExamScheduleAndSnapshot() {
        plannerRole(20L);
        DeliveryClassDO current = deliveryClass(100L, false, 8L, 20L);
        current.setExamScheduleId(70L).setExamScheduleSnapshot("原考期 · 2026-12-01").setVersion(2);
        when(classMapper.selectByIdForUpdate(100L, 1L)).thenReturn(current);
        when(adminUserApi.getUser(9L)).thenReturn(enabledUser(9L).setDeptId(80L));
        when(adminUserApi.getUser(20L)).thenReturn(enabledUser(20L).setDeptId(80L));
        when(classMapper.update(eq(current), any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(1);

        DeliveryClassSaveReqVO request = new DeliveryClassSaveReqVO();
        request.setClassName("改名后的班级"); request.setHomeroomUserId(20L);
        request.setPreserveExamDate(true); request.setVersion(2);

        service.update(100L, request, 9L);

        assertEquals(70L, current.getExamScheduleId());
        assertEquals("原考期 · 2026-12-01", current.getExamScheduleSnapshot());
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
            org.junit.jupiter.api.Assertions.assertTrue(fields.containsAll(java.util.List.of("className", "examDateValid")));
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

    @Test void multiDayOptionsIncludeEndDayAndExcludeEndedRanges() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "multi-day-options"), ExamScheduleDO.class);
        var today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
        var ongoing = new ExamScheduleDO().setId(7L).setScheduleName("多日考试").setScheduleType("MULTI_DAY")
                .setStartDate(today.minusDays(2)).setEndDate(today).setRecordStatus("PUBLISHED");
        var ended = new ExamScheduleDO().setId(8L).setScheduleType("MULTI_DAY")
                .setStartDate(today.minusDays(4)).setEndDate(today.minusDays(1)).setRecordStatus("PUBLISHED");
        when(scheduleMapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(java.util.List.of(ongoing, ended));
        var options = service.examOptions(null,null,null,null);
        assertEquals(1,options.size());
        assertEquals("MULTI_DAY",options.getFirst().scheduleType());
        assertEquals("多日考试 · " + today.minusDays(2) + "~" + today,options.getFirst().displayName());
        assertEquals(today.minusDays(2), options.getFirst().startDate());
        assertEquals(today, options.getFirst().endDate());
    }

    @Test void manualDatesCreateWithoutCalendarAndOverrideReferenceId() {
        plannerRole(20L);
        when(adminUserApi.getUser(9L)).thenReturn(enabledUser(9L).setDeptId(80L));
        when(adminUserApi.getUser(20L)).thenReturn(enabledUser(20L).setDeptId(80L));
        when(numberService.next()).thenReturn("BJTEST");
        var req = new DeliveryClassSaveReqVO(); req.setClassName("自主日期班"); req.setHomeroomUserId(20L);
        req.setStartDate(java.time.LocalDate.of(2026, 10, 1));
        req.setEndDate(java.time.LocalDate.of(2026, 10, 3));
        req.setExamScheduleId(999L);
        service.create(req, 9L);
        var saved = org.mockito.ArgumentCaptor.forClass(DeliveryClassDO.class);
        verify(classMapper).insert(saved.capture());
        assertEquals("2026-10-01~2026-10-03", saved.getValue().getExamScheduleSnapshot());
        org.junit.jupiter.api.Assertions.assertNull(saved.getValue().getExamScheduleId());
        verifyNoInteractions(scheduleMapper);
    }

    @Test void editingLegacyClassClearsAssociationAndReadsFrozenDates() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "class-dates"), DeliveryClassDO.class);
        plannerRole(20L);
        var current = deliveryClass(100L, false, null, 20L).setExamScheduleId(70L).setVersion(2);
        when(classMapper.selectByIdForUpdate(100L, 1L)).thenReturn(current);
        when(adminUserApi.getUser(9L)).thenReturn(enabledUser(9L).setDeptId(80L));
        when(adminUserApi.getUser(20L)).thenReturn(enabledUser(20L).setDeptId(80L));
        when(classMapper.update(eq(current), any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenAnswer(call -> {
            var wrapper = (com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<DeliveryClassDO>) call.getArgument(1);
            org.junit.jupiter.api.Assertions.assertTrue(wrapper.getSqlSet().contains("exam_schedule_id="));
            org.junit.jupiter.api.Assertions.assertTrue(wrapper.getParamNameValuePairs().containsValue(null));
            return 1;
        });
        var req = new DeliveryClassSaveReqVO(); req.setClassName("调整后"); req.setHomeroomUserId(20L); req.setVersion(2);
        req.setStartDate(java.time.LocalDate.of(2026, 11, 5));
        service.update(100L, req, 9L);
        org.junit.jupiter.api.Assertions.assertNull(current.getExamScheduleId());
        assertEquals("2026-11-05", current.getExamScheduleSnapshot());
        when(classMapper.selectById(100L)).thenReturn(current);
        assertEquals(req.getStartDate(), service.get(100L, 9L).getStartDate());
        current.setExamScheduleId(70L).setExamScheduleSnapshot("旧考期 · 2026-10-01~2026-10-03");
        assertEquals(java.time.LocalDate.of(2026, 10, 3), service.get(100L, 9L).getEndDate());
        verifyNoInteractions(scheduleMapper, relationMapper);
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
