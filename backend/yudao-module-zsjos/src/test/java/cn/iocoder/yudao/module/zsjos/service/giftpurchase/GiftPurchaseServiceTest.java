package cn.iocoder.yudao.module.zsjos.service.giftpurchase;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.giftpurchase.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.gift.GiftConfigDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.giftpurchase.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.giftpurchase.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.*;
import cn.iocoder.yudao.module.zsjos.service.gift.GiftConfigService;
import cn.iocoder.yudao.module.zsjos.service.order.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GiftPurchaseServiceTest {
    GiftPurchaseService service;
    GiftPurchaseMapper mapper;
    GiftPurchaseOperationMapper operations;
    GiftConfigService gifts;
    AdminUserApi users;
    SalesOrderMapper orders;
    SalesOrderApprovalRoundMapper rounds;

    @BeforeAll static void metadata() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "fixture"), GiftPurchaseDO.class);
    }
    @BeforeEach void setup() {
        service = new GiftPurchaseService(); mapper = mock(GiftPurchaseMapper.class);
        operations = mock(GiftPurchaseOperationMapper.class); gifts = mock(GiftConfigService.class);
        users = mock(AdminUserApi.class); orders = mock(SalesOrderMapper.class); rounds = mock(SalesOrderApprovalRoundMapper.class);
        ReflectionTestUtils.setField(service, "mapper", mapper);
        ReflectionTestUtils.setField(service, "operationMapper", operations);
        ReflectionTestUtils.setField(service, "giftConfigService", gifts);
        ReflectionTestUtils.setField(service, "userApi", users);
        ReflectionTestUtils.setField(service, "orderMapper", orders);
        ReflectionTestUtils.setField(service, "roundMapper", rounds);
        var actor = new AdminUserRespDTO(); actor.setId(44L); actor.setNickname("操作人快照");
        when(users.getUser(44L)).thenReturn(actor);
        var sales = new AdminUserRespDTO(); sales.setId(55L); sales.setNickname("录单销售快照");
        when(users.getUser(55L)).thenReturn(sales);
    }

    GiftPurchaseSaveReqVO request() {
        var req = new GiftPurchaseSaveReqVO(); req.setStudentName("测试学员"); req.setShippingAddress("测试地址");
        req.setGiftItemCodes(List.of("gift")); return req;
    }

    GiftPurchaseDO row() {
        var row = new GiftPurchaseDO(); row.setId(12L); row.setPurchased(false);
        row.setGiftItemsJson(JsonUtils.toJsonString(List.of(new SalesOrderGiftSnapshot("gift", "原礼品", List.of("原礼品"), "2026-01-01"))));
        row.setGeneratedAt(LocalDateTime.of(2026, 1, 1, 12, 0)); return row;
    }

    @Test void objectScopeRequiresTenantMatchAndConfiguredBatchPermission() {
        var provider = new GiftPurchasePermissionProvider();
        var permissions = mock(cn.iocoder.yudao.module.system.api.permission.PermissionApi.class);
        ReflectionTestUtils.setField(provider, "mapper", mapper);
        ReflectionTestUtils.setField(provider, "permissions", permissions);
        var row = row(); row.setTenantId(1L); when(mapper.selectById(12L)).thenReturn(row);
        cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(1L);
        try {
            assertFalse(provider.hasPermission(12L, "batch-purchase", 44L));
            when(permissions.hasAnyPermissions(44L, "zsjos:gift-purchase:batch-purchase")).thenReturn(true);
            assertTrue(provider.hasPermission(12L, "batch-purchase", 44L));
            assertFalse(provider.hasPermission(12L, "batch-purchase", null));
            assertFalse(provider.hasPermission(12L, "unsupported", 44L));
            row.setTenantId(2L);
            assertFalse(provider.hasPermission(12L, "batch-purchase", 44L));
            assertThrows(ServiceException.class, () -> provider.check(12L, "batch-purchase", 44L));
        } finally { cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear(); }
    }

    @Test void missingBatchRowPreventsWritesAndBatchInputIsBounded() {
        when(mapper.selectOne(any())).thenReturn(row(), null);
        var req = new GiftPurchaseBatchReqVO(); req.setIds(List.of(12L,13L));
        assertThrows(ServiceException.class, () -> service.batchPurchase(req));
        verify(mapper, never()).update(isNull(), any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        verifyNoInteractions(operations);
        try(var factory=jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var validator=factory.getValidator();
            req.setIds(List.of()); assertFalse(validator.validate(req).isEmpty());
            req.setIds(List.of(-1L)); assertFalse(validator.validate(req).isEmpty());
            req.setIds(java.util.Collections.nCopies(201,1L)); assertFalse(validator.validate(req).isEmpty());
        }
    }

    @Test void batchDeduplicatesSkipsPurchasedAndChecksAllBeforeWrites() {
        var access = mock(GiftPurchaseBatchItemService.class);
        ReflectionTestUtils.setField(service, "batchItemService", access);
        var a = row(); var b = row(); b.setId(13L); b.setPurchased(true);
        when(mapper.selectOne(any())).thenReturn(a, b);
        var req = new GiftPurchaseBatchReqVO(); req.setIds(List.of(13L, 12L, 12L));
        var result = service.batchPurchase(req);
        assertEquals(1, result.purchased()); assertEquals(1, result.skipped());
        var order = inOrder(access, mapper, operations);
        order.verify(access).check(12L); order.verify(access).check(13L);
        order.verify(mapper).update(isNull(), any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        order.verify(operations).insert(any(GiftPurchaseOperationDO.class));
    }

    @Test void batchDeniedObjectPreventsEveryWrite() {
        var access = mock(GiftPurchaseBatchItemService.class);
        ReflectionTestUtils.setField(service, "batchItemService", access);
        var a = row(); var b = row(); b.setId(13L);
        when(mapper.selectOne(any())).thenReturn(a,b);
        doThrow(new IllegalArgumentException("denied")).when(access).check(13L);
        var req = new GiftPurchaseBatchReqVO(); req.setIds(List.of(12L,13L));
        assertThrows(IllegalArgumentException.class, () -> service.batchPurchase(req));
        verify(mapper, never()).update(isNull(), any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        verifyNoInteractions(operations);
    }

    @Test void workbookIncludesHistoryAndPreservesStringsAndSnapshots() throws Exception {
        var row = new GiftPurchaseRespVO(); row.setId(7L); row.setStudentName("=1+1");
        row.setStudentMobile("0013800000000"); row.setShippingAddress("完整地址".repeat(100));
        row.setGiftItemSnapshots(List.of(new SalesOrderGiftSnapshot("old","历史礼品",List.of("原分类"),"2026-01-01")));
        var op = new GiftPurchaseOperationRespVO(); op.setAction("PURCHASE"); op.setOperatorName("历史操作人");
        byte[] bytes = GiftPurchaseWorkbook.write(List.of(row), java.util.Map.of(7L,List.of(op)));
        try(var book = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))) {
            assertEquals(2,book.getNumberOfSheets());
            var data=book.getSheetAt(0).getRow(1);
            assertEquals(org.apache.poi.ss.usermodel.CellType.STRING,data.getCell(2).getCellType());
            assertEquals("=1+1",data.getCell(2).getStringCellValue());
            assertEquals("0013800000000",data.getCell(3).getStringCellValue());
            assertEquals("历史礼品",data.getCell(5).getStringCellValue());
            assertEquals(row.getShippingAddress(),data.getCell(6).getStringCellValue());
            assertEquals("历史未记录",data.getCell(9).getStringCellValue());
            assertEquals("历史操作人",book.getSheetAt(1).getRow(1).getCell(4).getStringCellValue());
        }
    }

    @Test void manualCreateHasNoOrderAndCapturesActualCreator() {
        var gift = new GiftConfigDO(); gift.setId(1L); gift.setCode("gift"); gift.setName("新礼品"); gift.setStatus(0);
        when(gifts.listFlat(null)).thenReturn(List.of(gift));
        doAnswer(invocation -> { ((GiftPurchaseDO) invocation.getArgument(0)).setId(12L); return 1; }).when(mapper).insert(any(GiftPurchaseDO.class));
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(44L);
            assertEquals(12L, service.create(request()));
        }
        var saved = ArgumentCaptor.forClass(GiftPurchaseDO.class); verify(mapper).insert(saved.capture());
        assertNull(saved.getValue().getOrderId()); assertNull(saved.getValue().getOrderNo());
        assertFalse(saved.getValue().getPurchased()); assertNotNull(saved.getValue().getGeneratedAt());
        assertNull(saved.getValue().getSubmitterUserName());
        assertEquals("新礼品", SalesOrderGiftSnapshot.read(saved.getValue().getGiftItemsJson()).getFirst().name());
        verify(users, never()).validateUser(anyLong());
        var audit = ArgumentCaptor.forClass(GiftPurchaseOperationDO.class); verify(operations).insert(audit.capture());
        assertEquals("CREATE", audit.getValue().getAction()); assertEquals(44L, audit.getValue().getOperatorUserId());
        assertEquals("操作人快照", audit.getValue().getOperatorName());
    }

    @Test void editRetainsHistoricalGiftAndUnchangedSalespersonWithoutResolvingCurrentCatalogOrUser() {
        var row = row(); row.setSubmitterUserId(55L); row.setSubmitterUserName("历史销售");
        when(mapper.selectOne(any())).thenReturn(row);
        var req = request(); req.setId(12L);
        service.update(req);
        assertEquals("原礼品", SalesOrderGiftSnapshot.read(row.getGiftItemsJson()).getFirst().name());
        assertEquals("历史销售", row.getSubmitterUserName());
        assertEquals(LocalDateTime.of(2026, 1, 1, 12, 0), row.getGeneratedAt());
        verifyNoInteractions(gifts, users, orders);
        verify(mapper).update(isNull(), any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    }

    @Test void linkedEditCannotReassignOrderOrSalespersonAndNeverWritesOrders() {
        var row = row(); row.setOrderId(7L); row.setOrderNo("ORDER-7"); row.setSubmitterUserId(66L);
        when(mapper.selectOne(any())).thenReturn(row);
        var req = request(); req.setId(12L); service.update(req);
        assertEquals(7L, row.getOrderId()); assertEquals("ORDER-7", row.getOrderNo()); assertEquals(66L, row.getSubmitterUserId());
        verifyNoInteractions(orders, users, gifts);
    }

    @Test void missingOrTenantHiddenPurchaseRejectsEveryMutationWithoutAudit() {
        when(mapper.selectOne(any())).thenReturn(null);
        var req = request(); req.setId(99L);
        for (Runnable action : List.<Runnable>of(() -> service.get(99L), () -> service.update(req),
                () -> service.setPurchased(99L, true), () -> service.delete(99L))) {
            assertEquals(1_900_157_000, assertThrows(ServiceException.class, action::run).getCode());
        }
        verifyNoInteractions(operations, users, gifts);
    }

    @Test void purchaseAndUnpurchaseAreIdempotentCommandsAndDeletionRetainsHistory() {
        var row = row(); when(mapper.selectOne(any())).thenReturn(row);
        service.setPurchased(12L, false); verifyNoInteractions(operations);
        service.setPurchased(12L, true);
        row.setPurchased(true); service.setPurchased(12L, true);
        service.setPurchased(12L, false); service.delete(12L);
        var audit = ArgumentCaptor.forClass(GiftPurchaseOperationDO.class); verify(operations, times(3)).insert(audit.capture());
        assertEquals(List.of("PURCHASE", "UNPURCHASE", "DELETE"), audit.getAllValues().stream().map(GiftPurchaseOperationDO::getAction).toList());
        verify(mapper).deleteById(12L); verify(operations, never()).delete(any());
    }

    @Test void automaticAndManualProjectionsUseRecordedSourceNamesWithoutCurrentUserLookup() {
        var row = row(); row.setOrderId(7L);
        var order = new SalesOrderDO(); order.setId(7L); order.setTenantId(1L); order.setCurrentApprovalRoundId(8L); order.setSubmitterUserId(55L);
        var snapshot = new SalesOrderSnapshot(); snapshot.setSubmitter(new SalesOrderSnapshot.Actor("ADMIN",55L,"原录单销售",null));
        var round = new SalesOrderApprovalRoundDO(); round.setId(8L); round.setOrderSnapshot(JsonUtils.toJsonString(snapshot));
        when(mapper.selectOne(any())).thenReturn(row); when(orders.selectByIds(anyCollection())).thenReturn(List.of(order));
        when(rounds.selectByIds(anyCollection())).thenReturn(List.of(round));
        var op = new GiftPurchaseOperationDO(); op.setPurchaseId(12L); op.setOperatorName("原操作人");
        when(operations.selectList(any())).thenReturn(List.of(op));
        var response = service.get(12L);
        assertEquals("原录单销售", response.getSubmitterUserName()); assertEquals("原操作人", response.getOperatorName());
        assertEquals("ORDER", response.getSourceType()); assertEquals("原录单销售", response.getSourceUserName());
        assertEquals(55L, response.getSourceUserId());
        round.setOrderSnapshot(null); assertNull(service.get(12L).getSubmitterUserName());
        row.setOrderId(null); row.setSubmitterUserName("手工销售快照");
        assertEquals("手工销售快照", service.get(12L).getSubmitterUserName());
        assertEquals("UNKNOWN", service.get(12L).getSourceType());
        assertNull(service.get(12L).getSourceUserName());
        verifyNoInteractions(users);
    }

    private GiftPurchaseOperationDO operation(long id, String action, long actorId, String name) {
        var op = new GiftPurchaseOperationDO(); op.setId(id); op.setPurchaseId(12L); op.setAction(action);
        op.setOperatorUserId(actorId); op.setOperatorName(name);
        op.setOccurredAt(LocalDateTime.of(2026, 10, 21, 10, (int) id)); return op;
    }

    @Test void sourceIsOriginalCreatorAndMarkerIsPurchaseActorDespiteLaterEdits() {
        var row = row(); row.setPurchased(true); row.setSubmitterUserName("旧版手选销售，不能充当来源人");
        when(mapper.selectOne(any())).thenReturn(row);
        var creator = operation(1, "CREATE", 44, "原创建人");
        var marker = operation(2, "PURCHASE", 88, "采购标记人");
        var editor = operation(3, "UPDATE", 99, "后来编辑人");
        when(operations.selectList(any())).thenReturn(List.of(editor, marker, creator));
        var response = service.get(12L);
        assertEquals("MANUAL", response.getSourceType()); assertEquals(44L, response.getSourceUserId());
        assertEquals("原创建人", response.getSourceUserName());
        assertEquals(88L, response.getPurchasedByUserId()); assertEquals("采购标记人", response.getPurchasedByName());
        assertEquals(marker.getOccurredAt(), response.getPurchasedAt());
        row.setPurchased(false);
        assertNull(service.get(12L).getPurchasedByName()); assertNull(service.get(12L).getPurchasedAt());
        row.setPurchased(true);
        var remarker = operation(5, "PURCHASE", 66, "重新标记人");
        when(operations.selectList(any())).thenReturn(List.of(operation(6,"UPDATE",99,"再次编辑"),remarker,editor,marker,creator));
        assertEquals("重新标记人", service.get(12L).getPurchasedByName());
        assertEquals("原创建人", service.get(12L).getSourceUserName());
        verifyNoInteractions(users);
    }

    @Test void historicalPurchasedRecordWithoutAuditNeverBorrowsLatestEditorAsMarkerOrCreator() {
        var row = row(); row.setPurchased(true); when(mapper.selectOne(any())).thenReturn(row);
        when(operations.selectList(any())).thenReturn(List.of(operation(7,"UPDATE",99,"编辑人")));
        var response = service.get(12L);
        assertEquals("UNKNOWN", response.getSourceType()); assertNull(response.getSourceUserName());
        assertNull(response.getPurchasedByName()); assertNull(response.getPurchasedAt());
        verifyNoInteractions(users);
    }

    @Test void automaticGenerationCapturesSystemSourceAndAllMutationsHaveRollbackBoundaries() throws Exception {
        service.recordGenerated(12L);
        var audit = ArgumentCaptor.forClass(GiftPurchaseOperationDO.class); verify(operations).insert(audit.capture());
        assertEquals("GENERATE", audit.getValue().getAction()); assertEquals("系统", audit.getValue().getOperatorName());
        assertNull(audit.getValue().getOperatorUserId());
        for (var method : GiftPurchaseService.class.getDeclaredMethods()) {
            if (List.of("create", "update", "setPurchased", "delete", "recordGenerated").contains(method.getName())) {
                assertEquals(Exception.class, method.getAnnotation(org.springframework.transaction.annotation.Transactional.class).rollbackFor()[0]);
            }
        }
    }

    @Test void orderGenerationAuditsOnlySuccessfulNewRowsAndPreservesOneRowPerOrder() {
        var orderService = new SalesOrderServiceImpl();
        ReflectionTestUtils.setField(orderService, "giftPurchaseMapper", mapper);
        var auditService = mock(GiftPurchaseService.class);
        ReflectionTestUtils.setField(orderService, "giftPurchaseService", auditService);
        var order = new SalesOrderDO(); order.setId(7L); order.setOrderNo("ORDER-7"); order.setStudentName("学员");
        order.setGiftItems(row().getGiftItemsJson());
        when(mapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> { ((GiftPurchaseDO) invocation.getArgument(0)).setId(12L); return 1; }).when(mapper).insert(any(GiftPurchaseDO.class));
        cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(1L);
        try {
            ReflectionTestUtils.invokeMethod(orderService, "createGiftPurchaseIfNeeded", order, LocalDateTime.now());
            verify(auditService).recordGenerated(12L);
            clearInvocations(auditService);
            when(mapper.selectCount(any())).thenReturn(1L);
            ReflectionTestUtils.invokeMethod(orderService, "createGiftPurchaseIfNeeded", order, LocalDateTime.now());
            verifyNoInteractions(auditService);
            when(mapper.selectCount(any())).thenReturn(0L);
            doThrow(new org.springframework.dao.DuplicateKeyException("fixture duplicate")).when(mapper).insert(any(GiftPurchaseDO.class));
            ReflectionTestUtils.invokeMethod(orderService, "createGiftPurchaseIfNeeded", order, LocalDateTime.now());
            verifyNoInteractions(auditService);
        } finally { cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear(); }
    }

    @Test void corruptedGiftHistoryAndEmptyNewSelectionAreNotSilentlySaved() {
        var row = row(); row.setGiftItemsJson("broken"); when(mapper.selectOne(any())).thenReturn(row);
        assertThrows(ServiceException.class, () -> service.update(request()));
        var req = request(); req.setGiftItemCodes(List.of());
        assertEquals(1_900_157_001, assertThrows(ServiceException.class, () -> service.create(req)).getCode());
        verifyNoInteractions(operations);
    }
}
