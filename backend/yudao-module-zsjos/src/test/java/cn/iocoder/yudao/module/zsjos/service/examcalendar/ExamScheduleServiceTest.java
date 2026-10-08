package cn.iocoder.yudao.module.zsjos.service.examcalendar;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.infra.api.config.ConfigApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.examcalendar.vo.ExamSchedulePageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.examcalendar.vo.ExamScheduleSaveReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.ZsjosProductCategoryPathNodeVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcalendar.ExamScheduleDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.product.ZsjosProductCategoryDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcalendar.ExamScheduleMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.product.ZsjosProductCategoryMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExamScheduleServiceTest {
    private static final LocalDate BUSINESS_TODAY = LocalDate.of(2026, 10, 8);

    @org.junit.jupiter.api.BeforeEach void fixBusinessClock() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "clock", java.time.Clock.fixed(
                java.time.Instant.parse("2026-10-08T01:00:00Z"), java.time.ZoneId.of("Asia/Shanghai")));
    }
    @org.junit.jupiter.api.BeforeAll static void initializeTableMetadata() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "exam-test"), ExamScheduleDO.class);
    }
    @InjectMocks private ExamScheduleService service;
    @Mock private ExamScheduleMapper mapper;
    @Mock private ZsjosProductCategoryMapper categoryMapper;
    @Mock private PermissionApi permissionApi;
    @Mock private ConfigApi configApi;
    @Mock private cn.iocoder.yudao.module.zsjos.service.calendar.CalendarNotificationSnapshotService notificationSnapshots;
    @Mock private cn.iocoder.yudao.module.zsjos.service.product.ProductCategoryLocks categoryLocks;
    @Mock private cn.iocoder.yudao.module.zsjos.service.product.ZsjosProductSkuService productSkuService;

    @Test
    void displayStatusUsesExactDateBoundaries() {
        LocalDate examDate = LocalDate.of(2026, 9, 10);
        ExamScheduleDO schedule = new ExamScheduleDO().setScheduleType("EXACT")
                .setExactDate(examDate).setRecordStatus("PUBLISHED");
        assertEquals("PUBLISHED", service.displayStatus(schedule, LocalDate.of(2026, 9, 6), 3));
        assertEquals("UPCOMING", service.displayStatus(schedule, LocalDate.of(2026, 9, 7), 3));
        assertEquals("IN_PROGRESS", service.displayStatus(schedule, examDate, 3));
        assertEquals("ENDED", service.displayStatus(schedule, LocalDate.of(2026, 9, 11), 3));
        schedule.setRecordStatus("REVOKED");
        assertEquals("REVOKED", service.displayStatus(schedule, examDate, 3));
    }





    @Test void revokePreviewKeepsFrozenContentAndRejectsDraft() {
        when(permissionApi.hasAnyPermissions(20L, ExamScheduleService.PERMISSION_MANAGE)).thenReturn(true);
        var published = new ExamScheduleDO().setId(9L).setCalendarVersion(3).setRecordStatus("PUBLISHED")
                .setFrozenSkusJson("[]").setProductNameSnapshot("历史产品");
        when(mapper.selectById(9L)).thenReturn(published);
        var preview = service.previewTransition(9L, "REVOKED", 20L);
        assertEquals("REVOKED", preview.getRecordStatus()); assertEquals(4, preview.getCalendarVersion());
        assertEquals("历史产品", preview.getProductNameSnapshot()); assertEquals("[]", preview.getFrozenSkusJson());
        assertEquals("PUBLISHED", published.getRecordStatus());
        published.setRecordStatus("DRAFT");
        assertThrows(ServiceException.class, () -> service.previewTransition(9L, "REVOKED", 20L));
        verifyNoInteractions(categoryLocks, productSkuService, notificationSnapshots);
    }



    @Test
    void configuredDaysFallsBackForMissingNegativeAndInvalidValues() {
        when(configApi.getConfigValueByKey(ExamScheduleService.UPCOMING_DAYS_CONFIG_KEY))
                .thenReturn("5", "-1", "bad", null);
        assertEquals(5, service.configuredUpcomingDays());
        assertEquals(3, service.configuredUpcomingDays());
        assertEquals(3, service.configuredUpcomingDays());
        assertEquals(3, service.configuredUpcomingDays());
    }





    @Test
    void createRequiresManagePermissionBeforeWriting() {
        when(permissionApi.hasAnyPermissions(20L, ExamScheduleService.PERMISSION_MANAGE)).thenReturn(false);
        ServiceException error = assertThrows(ServiceException.class, () -> service.create(exactReq(), 20L));
        assertEquals(1_900_018_006, error.getCode());
        verify(mapper, never()).insert(any(ExamScheduleDO.class));
    }



    @Test
    void rejectsInvalidDateShapes() {
        when(permissionApi.hasAnyPermissions(20L, ExamScheduleService.PERMISSION_MANAGE)).thenReturn(true);
        ExamScheduleSaveReqVO exact = exactReq();
        exact.setStartDate(exact.getExactDate());
        assertEquals(1_900_018_003, assertThrows(ServiceException.class,
                () -> service.create(exact, 20L)).getCode());

        ExamScheduleSaveReqVO multiDay = multiDayReq();
        multiDay.setEndDate(multiDay.getStartDate().minusDays(1));
        assertEquals(1_900_018_003, assertThrows(ServiceException.class,
                () -> service.create(multiDay, 20L)).getCode());


    }

    @Test
    void queryIncludesDraftsOnlyForManagers() {
        ExamSchedulePageReqVO req = new ExamSchedulePageReqVO();
        req.setPageNo(1); req.setPageSize(20);
        when(permissionApi.hasAnyPermissions(20L, ExamScheduleService.PERMISSION_MANAGE)).thenReturn(false);
        when(permissionApi.hasAnyPermissions(21L, ExamScheduleService.PERMISSION_MANAGE)).thenReturn(true);
        when(mapper.selectExactList(eq(req), eq(false), any())).thenReturn(List.of());
        when(mapper.selectExactList(eq(req), eq(true), any())).thenReturn(List.of());
        service.exactPage(req, 20L);
        service.exactPage(req, 21L);
        verify(mapper).selectExactList(eq(req), eq(false), any());
        verify(mapper).selectExactList(eq(req), eq(true), any());
    }

    @Test
    void publishAndRevokeEnforceLifecycle() {
        when(permissionApi.hasAnyPermissions(20L, ExamScheduleService.PERMISSION_MANAGE)).thenReturn(true);
        when(mapper.selectForUpdate(9L)).thenReturn(new ExamScheduleDO().setId(9L).setCategoryId(2L).setRecordStatus("DRAFT").setCalendarVersion(3),
                new ExamScheduleDO().setId(9L).setRecordStatus("PUBLISHED").setCalendarVersion(4));
        service.publish(9L, 20L);
        service.revoke(9L, 20L);
        ArgumentCaptor<ExamScheduleDO> updates = ArgumentCaptor.forClass(ExamScheduleDO.class);
        verify(mapper, times(2)).updateById(updates.capture());
        assertEquals(List.of("PUBLISHED", "REVOKED"),
                updates.getAllValues().stream().map(ExamScheduleDO::getRecordStatus).toList());
        assertEquals(List.of(4, 5), updates.getAllValues().stream().map(ExamScheduleDO::getCalendarVersion).toList());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void draftVersionOnlyChangesWithNotificationContent(boolean changed) {
        when(permissionApi.hasAnyPermissions(20L, ExamScheduleService.PERMISSION_MANAGE)).thenReturn(true);
        var req = exactReq();
        var current = new ExamScheduleDO().setId(9L).setRecordStatus("DRAFT").setCalendarVersion(5)
                .setScheduleType(req.getScheduleType()).setExactDate(req.getExactDate()).setRemark(req.getRemark())
                .setScheduleName(req.getScheduleName());
        when(mapper.selectForUpdate(9L)).thenReturn(current);
        if (changed) req.setRemark("下午场");
        service.update(9L, req, 20L);
        var saved = ArgumentCaptor.forClass(ExamScheduleDO.class);
        verify(mapper).update(saved.capture(), any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        assertEquals(changed ? 6 : 5, saved.getValue().getCalendarVersion());
        assertEquals(req.getScheduleName(), saved.getValue().getScheduleName());
    }

    @Test
    void rejectsPublishingOrUpdatingExactSchedulesIntoHistory() {
        when(permissionApi.hasAnyPermissions(20L, ExamScheduleService.PERMISSION_MANAGE)).thenReturn(true);
        LocalDate yesterday = BUSINESS_TODAY.minusDays(1);
        when(mapper.selectForUpdate(9L)).thenReturn(new ExamScheduleDO().setId(9L).setScheduleType("EXACT")
                .setExactDate(yesterday).setRecordStatus("DRAFT"));
        assertEquals(1_900_018_007,
                assertThrows(ServiceException.class, () -> service.publish(9L, 20L)).getCode());

        ExamScheduleSaveReqVO update = exactReq();
        update.setExactDate(yesterday);
        when(mapper.selectForUpdate(10L)).thenReturn(new ExamScheduleDO().setId(10L).setScheduleType("EXACT")
                .setExactDate(yesterday.plusDays(2)).setRecordStatus("DRAFT"));
        assertEquals(1_900_018_014,
                assertThrows(ServiceException.class, () -> service.update(10L, update, 20L)).getCode());
        verify(mapper, never()).updateById(any(ExamScheduleDO.class));
    }

    @Test void createsAndPublishesWithoutCatalogAndKeepsManualName() {
        when(permissionApi.hasAnyPermissions(20L, ExamScheduleService.PERMISSION_MANAGE)).thenReturn(true);
        var req = exactReq(); req.setScheduleName("  自定义秋季场  ");
        // Even obsolete client fields cannot reinstate a product association.
        req.setProductId(8L); req.setCategoryId(2L);
        service.create(req, 20L);
        var saved = ArgumentCaptor.forClass(ExamScheduleDO.class);
        verify(mapper).insert(saved.capture());
        var row = saved.getValue(); row.setId(9L);
        assertEquals("自定义秋季场", row.getScheduleName()); assertNull(row.getProductId()); assertNull(row.getCategoryId());
        when(mapper.selectForUpdate(9L)).thenReturn(row);
        when(mapper.selectById(9L)).thenReturn(row);
        var preview = service.previewTransition(9L, "PUBLISHED", 20L);
        assertEquals("自定义秋季场", preview.getScheduleName()); assertEquals("DRAFT", row.getRecordStatus());
        service.publish(9L, 20L);
        assertEquals("PUBLISHED", row.getRecordStatus());
        assertEquals("自定义秋季场", cn.iocoder.yudao.module.zsjos.service.calendar.CalendarNotificationSnapshotService.projectExam(row, "PUBLISHED").getTitleSnapshot());
        verifyNoInteractions(categoryLocks, categoryMapper, productSkuService);
    }

    @Test void renamingIncrementsVersionAndClearsLegacyAssociation() {
        when(permissionApi.hasAnyPermissions(20L, ExamScheduleService.PERMISSION_MANAGE)).thenReturn(true);
        var req = exactReq();
        var legacy = new ExamScheduleDO().setId(9L).setRecordStatus("DRAFT").setCalendarVersion(4)
                .setProductId(8L).setProductNameSnapshot("历史名称").setSelectedSpecsJson("[]");
        assertEquals("历史名称", ExamScheduleService.displayName(legacy));
        when(mapper.selectForUpdate(9L)).thenReturn(legacy);
        service.update(9L, req, 20L);
        var saved = ArgumentCaptor.forClass(ExamScheduleDO.class);
        verify(mapper).update(saved.capture(), any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        assertEquals(5, saved.getValue().getCalendarVersion()); assertNull(saved.getValue().getProductId());
        assertEquals("自由考期", saved.getValue().getScheduleName());
        assertEquals("历史名称", legacy.getProductNameSnapshot());
        verifyNoInteractions(categoryLocks, categoryMapper, productSkuService);
    }

    @Test void manualNamesAreRequiredAtHttpBoundary() {
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var req = exactReq(); req.setScheduleName("  ");
            assertTrue(factory.getValidator().validate(req).stream().anyMatch(v -> v.getPropertyPath().toString().equals("scheduleName")));
        }
    }

    @Test void multiDayStatusIncludesStartMiddleAndEnd() {
        var row = new ExamScheduleDO().setScheduleType("MULTI_DAY").setStartDate(LocalDate.of(2026,10,10))
                .setEndDate(LocalDate.of(2026,10,20)).setRecordStatus("PUBLISHED");
        assertEquals("PUBLISHED", service.displayStatus(row, LocalDate.of(2026,10,6), 3));
        assertEquals("UPCOMING", service.displayStatus(row, LocalDate.of(2026,10,7), 3));
        for (int day : new int[]{10, 15, 20}) assertEquals("IN_PROGRESS", service.displayStatus(row, LocalDate.of(2026,10,day), 3));
        assertEquals("ENDED", service.displayStatus(row, LocalDate.of(2026,10,21), 3));
        row.setRecordStatus("DRAFT");
        assertEquals("DRAFT", service.displayStatus(row, LocalDate.of(2026,10,21), 3));
        row.setRecordStatus("REVOKED");
        assertEquals("REVOKED", service.displayStatus(row, LocalDate.of(2026,10,15), 3));
    }

    @Test void multiDayRejectsRetiredTypeAndEndedMaintenanceButAllowsOngoingPublish() {
        when(permissionApi.hasAnyPermissions(20L, ExamScheduleService.PERMISSION_MANAGE)).thenReturn(true);
        var req = multiDayReq(); req.setScheduleType("ROUGH");
        assertEquals(1_900_018_002, assertThrows(ServiceException.class, () -> service.create(req,20L)).getCode());
        var today = BUSINESS_TODAY;
        var row = new ExamScheduleDO().setId(9L).setScheduleType("MULTI_DAY").setStartDate(today.minusDays(3))
                .setEndDate(today.minusDays(1)).setRecordStatus("DRAFT").setCalendarVersion(1);
        when(mapper.selectForUpdate(9L)).thenReturn(row);
        when(mapper.selectById(9L)).thenReturn(row);
        assertThrows(ServiceException.class, () -> service.publish(9L,20L));
        assertThrows(ServiceException.class, () -> service.previewTransition(9L,"PUBLISHED",20L));
        service.update(9L,multiDayReq(),20L);
        row.setEndDate(today.plusDays(2));
        service.publish(9L,20L);
        assertEquals("PUBLISHED",row.getRecordStatus());
    }

    @Test void multiDaySnapshotUsesDefiniteDatesAndStableCanonicalHash() {
        var row = new ExamScheduleDO().setId(1L).setScheduleType("MULTI_DAY").setStartDate(LocalDate.of(2026,10,1))
                .setEndDate(LocalDate.of(2026,10,8)).setRecordStatus("PUBLISHED").setCalendarVersion(1).setScheduleName("多日考试");
        var snapshot = cn.iocoder.yudao.module.zsjos.service.calendar.CalendarNotificationSnapshotService.projectExam(row,"PUBLISHED");
        assertTrue(snapshot.getDetailsJson().contains("startDate"));
        assertFalse(snapshot.getDetailsJson().contains("rough"));
        assertEquals("2026-10-01 - 2026-10-08",snapshot.getTimeSnapshot());
    }

    @Test void rejectsPastCreationBeforeAnyWrite() {
        when(permissionApi.hasAnyPermissions(20L, ExamScheduleService.PERMISSION_MANAGE)).thenReturn(true);
        var yesterday = BUSINESS_TODAY.minusDays(1);
        for (var req : List.of(exactReq(), multiDayReq())) {
            if ("EXACT".equals(req.getScheduleType())) req.setExactDate(yesterday);
            else { req.setStartDate(yesterday.minusDays(2)); req.setEndDate(yesterday); }
            assertEquals(1_900_018_014, assertThrows(ServiceException.class,
                    () -> service.create(req, 20L)).getCode());
        }
        verifyNoInteractions(mapper, notificationSnapshots);
    }

    @Test void expiredDraftCanBeRescheduledButCannotRemainExpired() {
        when(permissionApi.hasAnyPermissions(20L, ExamScheduleService.PERMISSION_MANAGE)).thenReturn(true);
        var today = BUSINESS_TODAY;
        var row = new ExamScheduleDO().setId(9L).setScheduleType("EXACT")
                .setExactDate(today.minusDays(1)).setRecordStatus("DRAFT").setCalendarVersion(1);
        when(mapper.selectForUpdate(9L)).thenReturn(row);
        var req = exactReq(); req.setExactDate(today.minusDays(1));
        assertEquals(1_900_018_014, assertThrows(ServiceException.class,
                () -> service.update(9L, req, 20L)).getCode());
        verifyNoInteractions(notificationSnapshots);
        req.setExactDate(today);
        service.update(9L, req, 20L);
        var saved = ArgumentCaptor.forClass(ExamScheduleDO.class);
        verify(mapper).update(saved.capture(), any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        assertEquals(today, saved.getValue().getExactDate());
        assertEquals("DRAFT", saved.getValue().getRecordStatus());
    }

    @Test void createsTodayAndFutureExactAndOngoingMultiDay() {
        when(permissionApi.hasAnyPermissions(20L, ExamScheduleService.PERMISSION_MANAGE)).thenReturn(true);
        var today = BUSINESS_TODAY;
        for (int offset : List.of(0, 1)) {
            var exact = exactReq(); exact.setExactDate(today.plusDays(offset));
            service.create(exact, 20L);
            var multi = multiDayReq(); multi.setStartDate(today.minusDays(2)); multi.setEndDate(today.plusDays(offset));
            service.create(multi, 20L);
        }
        verify(mapper, times(4)).insert(any(ExamScheduleDO.class));
    }

    private static ExamScheduleSaveReqVO exactReq() {
        ExamScheduleSaveReqVO req = new ExamScheduleSaveReqVO();
        req.setScheduleType("EXACT"); req.setExactDate(LocalDate.of(2026, 10, 10));
        req.setScheduleName("自由考期"); req.setRemark("上午场");
        return req;
    }

    private static ExamScheduleSaveReqVO multiDayReq() {
        ExamScheduleSaveReqVO req = new ExamScheduleSaveReqVO();
        req.setScheduleType("MULTI_DAY"); req.setStartDate(LocalDate.of(2026, 10, 1));
        req.setEndDate(LocalDate.of(2026, 10, 15)); req.setScheduleName("自由考期");
        return req;
    }

    private static ZsjosProductCategoryDO category(Long id, Long parentId, String name, int status) {
        return new ZsjosProductCategoryDO().setId(id).setParentId(parentId).setName(name)
                .setStatus(status).setSort(1);
    }
}
