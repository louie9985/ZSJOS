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
        exact.setRoughStartDate(exact.getExactDate());
        assertEquals(1_900_018_003, assertThrows(ServiceException.class,
                () -> service.create(exact, 20L)).getCode());

        ExamScheduleSaveReqVO rough = roughReq();
        rough.setRoughEndDate(rough.getRoughStartDate().minusDays(1));
        assertEquals(1_900_018_003, assertThrows(ServiceException.class,
                () -> service.create(rough, 20L)).getCode());


    }

    @Test
    void queryIncludesDraftsOnlyForManagers() {
        ExamSchedulePageReqVO req = new ExamSchedulePageReqVO();
        req.setPageNo(1); req.setPageSize(20);
        when(permissionApi.hasAnyPermissions(20L, ExamScheduleService.PERMISSION_MANAGE)).thenReturn(false);
        when(permissionApi.hasAnyPermissions(21L, ExamScheduleService.PERMISSION_MANAGE)).thenReturn(true);
        when(mapper.selectExactList(req, false)).thenReturn(List.of());
        when(mapper.selectExactList(req, true)).thenReturn(List.of());
        service.exactPage(req, 20L);
        service.exactPage(req, 21L);
        verify(mapper).selectExactList(req, false);
        verify(mapper).selectExactList(req, true);
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
        LocalDate yesterday = LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).minusDays(1);
        when(mapper.selectForUpdate(9L)).thenReturn(new ExamScheduleDO().setId(9L).setScheduleType("EXACT")
                .setExactDate(yesterday).setRecordStatus("DRAFT"));
        assertEquals(1_900_018_007,
                assertThrows(ServiceException.class, () -> service.publish(9L, 20L)).getCode());

        ExamScheduleSaveReqVO update = exactReq();
        update.setExactDate(yesterday);
        when(mapper.selectForUpdate(10L)).thenReturn(new ExamScheduleDO().setId(10L).setScheduleType("EXACT")
                .setExactDate(yesterday.plusDays(2)).setRecordStatus("DRAFT"));
        assertEquals(1_900_018_007,
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

    private static ExamScheduleSaveReqVO exactReq() {
        ExamScheduleSaveReqVO req = new ExamScheduleSaveReqVO();
        req.setScheduleType("EXACT"); req.setExactDate(LocalDate.of(2026, 10, 10));
        req.setScheduleName("自由考期"); req.setRemark("上午场");
        return req;
    }

    private static ExamScheduleSaveReqVO roughReq() {
        ExamScheduleSaveReqVO req = new ExamScheduleSaveReqVO();
        req.setScheduleType("ROUGH"); req.setRoughStartDate(LocalDate.of(2026, 10, 1));
        req.setRoughEndDate(LocalDate.of(2026, 10, 15)); req.setScheduleName("自由考期");
        return req;
    }

    private static ZsjosProductCategoryDO category(Long id, Long parentId, String name, int status) {
        return new ZsjosProductCategoryDO().setId(id).setParentId(parentId).setName(name)
                .setStatus(status).setSort(1);
    }
}
