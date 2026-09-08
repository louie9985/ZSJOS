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

    @Test
    void productScopeIsFrozenAtPublicationAndQueriesNeverResolveCurrentCatalog() {
        when(permissionApi.hasAnyPermissions(20L, ExamScheduleService.PERMISSION_MANAGE)).thenReturn(true);
        var spec = new cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.ProductSpecVO("level", "考试等级", "2", "二级", false);
        var sku = new cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.ExamProductScopeRespVO.Sku(1L, "sku1", "二级班", java.util.Map.of("level", "2"), List.of(spec));
        var scope = new cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.ExamProductScopeRespVO(8L, "computer", "计算机考试", 2L,
                List.of(new ZsjosProductCategoryPathNodeVO(2L, "考试")), List.of(), List.of(spec), List.of(sku));
        when(productSkuService.resolveExamScope(8L, java.util.Map.of("level", "2"))).thenReturn(scope);
        ExamScheduleSaveReqVO req = exactReq(); req.setCategoryId(null); req.setProductId(8L); req.setSelectedAttrs(java.util.Map.of("level", "2"));
        service.create(req, 20L);
        var saved = ArgumentCaptor.forClass(ExamScheduleDO.class);
        verify(mapper).insert(saved.capture());
        assertNull(saved.getValue().getFrozenSkusJson());
        saved.getValue().setId(9L);
        when(mapper.selectForUpdate(9L)).thenReturn(saved.getValue());
        service.publish(9L, 20L);
        assertTrue(saved.getValue().getFrozenSkusJson().contains("sku1"));
        clearInvocations(productSkuService);
        var query = new ExamSchedulePageReqVO(); query.setPageNo(1); query.setPageSize(20);
        when(mapper.selectExactList(query, true)).thenReturn(List.of(saved.getValue()));
        var response = service.exactPage(query, 20L).getList().getFirst();
        assertEquals("计算机考试，考试等级：二级", response.getScheduleName());
        assertEquals(1, response.getFrozenSkus().size());
        verifyNoInteractions(productSkuService);
        assertEquals(1_900_018_005, assertThrows(ServiceException.class, () -> service.update(9L, req, 20L)).getCode());
    }

    @Test void productOptionsRequireManageAndCategoryRejectsSkuConditions() {
        assertThrows(ServiceException.class, () -> service.productOptions(20L));
        verifyNoInteractions(productSkuService);
        when(permissionApi.hasAnyPermissions(20L, ExamScheduleService.PERMISSION_MANAGE)).thenReturn(true);
        var req = exactReq(); req.setSelectedAttrs(java.util.Map.of("level", "2"));
        assertEquals(1_900_018_009, assertThrows(ServiceException.class, () -> service.create(req, 20L)).getCode());
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

    @Test void unchangedProductKeepsHistoricalPathAfterCatalogMove() {
        when(permissionApi.hasAnyPermissions(20L, ExamScheduleService.PERMISSION_MANAGE)).thenReturn(true);
        var current = new ExamScheduleDO().setId(9L).setRecordStatus("DRAFT").setProductId(8L).setCategoryId(2L)
                .setProductNameSnapshot("原产品").setCategoryNameSnapshot("原分类").setCategoryPathSnapshot("[]")
                .setSelectedAttrsJson("{}").setSelectedSpecsJson("[]");
        when(mapper.selectForUpdate(9L)).thenReturn(current);
        var scope = new cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.ExamProductScopeRespVO(8L, "p", "新名称", 3L,
                List.of(new ZsjosProductCategoryPathNodeVO(3L, "新分类")), List.of(), List.of(), List.of());
        when(productSkuService.resolveExamScope(8L, java.util.Map.of())).thenReturn(scope);
        var req = exactReq(); req.setCategoryId(null); req.setProductId(8L); req.setSelectedAttrs(java.util.Map.of());
        service.update(9L, req, 20L);
        var saved = ArgumentCaptor.forClass(ExamScheduleDO.class);
        verify(mapper).update(saved.capture(), any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        assertEquals("原产品", saved.getValue().getProductNameSnapshot());
        assertEquals("原分类", saved.getValue().getCategoryNameSnapshot());
        assertEquals("[]", saved.getValue().getCategoryPathSnapshot());
    }

    @Test void removedConditionRequiresExplicitClearAndDoesNotWriteOnFailure() {
        when(permissionApi.hasAnyPermissions(20L, ExamScheduleService.PERMISSION_MANAGE)).thenReturn(true);
        when(mapper.selectForUpdate(9L)).thenReturn(new ExamScheduleDO().setId(9L).setRecordStatus("DRAFT")
                .setProductId(8L).setCategoryId(2L).setSelectedAttrsJson("{\"removed\":\"old\"}"));
        var scope = new cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.ExamProductScopeRespVO(8L, "p", "产品", 2L,
                List.of(new ZsjosProductCategoryPathNodeVO(2L, "分类")), List.of(), List.of(), List.of());
        when(productSkuService.resolveExamScope(8L, java.util.Map.of())).thenReturn(scope);
        var req = exactReq(); req.setCategoryId(null); req.setProductId(8L); req.setSelectedAttrs(java.util.Map.of());
        assertEquals(1_900_018_010, assertThrows(ServiceException.class, () -> service.update(9L, req, 20L)).getCode());
        verify(mapper, never()).update(any(ExamScheduleDO.class), any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        req.setClearedInvalidAttrs(java.util.Set.of("removed"));
        service.update(9L, req, 20L);
        verify(mapper).update(any(ExamScheduleDO.class), any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    }

    @Test
    void createRequiresManagePermissionBeforeWriting() {
        when(permissionApi.hasAnyPermissions(20L, ExamScheduleService.PERMISSION_MANAGE)).thenReturn(false);
        ServiceException error = assertThrows(ServiceException.class, () -> service.create(exactReq(), 20L));
        assertEquals(1_900_018_006, error.getCode());
        verify(mapper, never()).insert(any(ExamScheduleDO.class));
    }

    @Test
    void createExactSnapshotsEnabledCategoryPath() {
        when(permissionApi.hasAnyPermissions(20L, ExamScheduleService.PERMISSION_MANAGE)).thenReturn(true);
        ZsjosProductCategoryDO root = category(1L, 0L, "教师资格", 0);
        ZsjosProductCategoryDO leaf = category(2L, 1L, "笔试", 0);
        when(categoryLocks.paths(List.of(2L))).thenReturn(java.util.Map.of(1L, root, 2L, leaf));
        doAnswer(invocation -> { invocation.<ExamScheduleDO>getArgument(0).setId(9L); return 1; })
                .when(mapper).insert(any(ExamScheduleDO.class));

        assertEquals(9L, service.create(exactReq(), 20L));

        ArgumentCaptor<ExamScheduleDO> captor = ArgumentCaptor.forClass(ExamScheduleDO.class);
        verify(mapper).insert(captor.capture());
        ExamScheduleDO saved = captor.getValue();
        assertEquals("DRAFT", saved.getRecordStatus());
        assertEquals("笔试", saved.getCategoryNameSnapshot());
        assertNull(saved.getRoughStartDate());
        List<ZsjosProductCategoryPathNodeVO> path = JsonUtils.parseArray(
                saved.getCategoryPathSnapshot(), ZsjosProductCategoryPathNodeVO.class);
        assertEquals(List.of(1L, 2L), path.stream().map(ZsjosProductCategoryPathNodeVO::id).toList());
    }

    @Test
    void rejectsDisabledCategoryAndInvalidDateShapes() {
        when(permissionApi.hasAnyPermissions(20L, ExamScheduleService.PERMISSION_MANAGE)).thenReturn(true);
        ExamScheduleSaveReqVO exact = exactReq();
        exact.setRoughStartDate(exact.getExactDate());
        assertEquals(1_900_018_003, assertThrows(ServiceException.class,
                () -> service.create(exact, 20L)).getCode());

        ExamScheduleSaveReqVO rough = roughReq();
        rough.setRoughEndDate(rough.getRoughStartDate().minusDays(1));
        assertEquals(1_900_018_003, assertThrows(ServiceException.class,
                () -> service.create(rough, 20L)).getCode());

        when(categoryLocks.paths(List.of(2L))).thenReturn(java.util.Map.of(2L, category(2L, 0L, "停用分类", 1)));
        assertEquals(1_900_018_004, assertThrows(ServiceException.class,
                () -> service.create(exactReq(), 20L)).getCode());
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
        when(categoryLocks.paths(List.of(2L))).thenReturn(java.util.Map.of(2L, category(2L, 0L, "考试", 0)));
        when(mapper.selectForUpdate(9L)).thenReturn(new ExamScheduleDO().setId(9L).setCategoryId(2L).setRecordStatus("DRAFT"),
                new ExamScheduleDO().setId(9L).setRecordStatus("PUBLISHED"));
        service.publish(9L, 20L);
        service.revoke(9L, 20L);
        ArgumentCaptor<ExamScheduleDO> updates = ArgumentCaptor.forClass(ExamScheduleDO.class);
        verify(mapper, times(2)).updateById(updates.capture());
        assertEquals(List.of("PUBLISHED", "REVOKED"),
                updates.getAllValues().stream().map(ExamScheduleDO::getRecordStatus).toList());
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

    private static ExamScheduleSaveReqVO exactReq() {
        ExamScheduleSaveReqVO req = new ExamScheduleSaveReqVO();
        req.setScheduleType("EXACT"); req.setExactDate(LocalDate.of(2026, 10, 10));
        req.setCategoryId(2L); req.setRemark("上午场");
        return req;
    }

    private static ExamScheduleSaveReqVO roughReq() {
        ExamScheduleSaveReqVO req = new ExamScheduleSaveReqVO();
        req.setScheduleType("ROUGH"); req.setRoughStartDate(LocalDate.of(2026, 10, 1));
        req.setRoughEndDate(LocalDate.of(2026, 10, 15)); req.setCategoryId(2L);
        return req;
    }

    private static ZsjosProductCategoryDO category(Long id, Long parentId, String name, int status) {
        return new ZsjosProductCategoryDO().setId(id).setParentId(parentId).setName(name)
                .setStatus(status).setSort(1);
    }
}
