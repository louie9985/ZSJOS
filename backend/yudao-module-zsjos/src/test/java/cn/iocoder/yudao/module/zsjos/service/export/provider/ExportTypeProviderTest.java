package cn.iocoder.yudao.module.zsjos.service.export.provider;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.cashback.vo.CashbackRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderListItemRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.FinanceOrderExportRowRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.withdrawal.vo.WithdrawalRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.export.ExportTaskDO;
import cn.iocoder.yudao.module.zsjos.service.cashback.CashbackService;
import cn.iocoder.yudao.module.zsjos.service.export.ExportTypeProvider;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadManagementService;
import cn.iocoder.yudao.module.zsjos.service.order.SalesOrderService;
import cn.iocoder.yudao.module.zsjos.service.order.SalesOrderObjectPermissionService;
import cn.iocoder.yudao.module.zsjos.service.withdrawal.WithdrawalService;
import cn.iocoder.yudao.module.zsjos.service.withdrawal.WithdrawalReviewService;
import cn.iocoder.yudao.module.zsjos.service.cashback.FinanceTraceService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.zsjos.controller.admin.withdrawal.vo.WithdrawalPageReqVO;
import org.apache.poi.ss.usermodel.CellType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExportTypeProviderTest {

    @Test
    void springRegistersAllFiveProviders() {
        try (var context = providerContext()) {
            assertEquals(List.of("cashback", "finance_order", "lead", "order", "withdrawal"), context
                    .getBeansOfType(ExportTypeProvider.class).values().stream()
                    .map(ExportTypeProvider::getType).sorted().toList());
        }
    }

    @Test
    void leadProviderGeneratesWorkbookWithWatermark() throws Exception {
        try (var context = providerContext()) {
            LeadManagementRespVO row = new LeadManagementRespVO();
            row.setId(9_007_199_254_740_993L);
            row.setLeadNo("KZ202608160000000001");
            row.setSubmittedName("测试客资");
            row.setSubmittedMobile("13800000000");
            when(context.getBean(LeadManagementService.class).getLeadPage(any(), eq(7L)))
                    .thenReturn(new PageResult<>(List.of(row), 1L));

            ExportTypeProvider.ExportResult result = context.getBean(LeadExportTypeProvider.class).generate(task("lead"));

            assertEquals(1L, result.rowCount());
            assertTrue(result.fileName().endsWith(".xlsx"));
            try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(result.content()))) {
                var sheet = workbook.getSheet("客资");
                assertNotNull(sheet);
                assertEquals("任务编号", sheet.getRow(0).getCell(0).getStringCellValue());
                assertEquals("EXP001", sheet.getRow(1).getCell(0).getStringCellValue());
                assertEquals("导出人", sheet.getRow(0).getCell(1).getStringCellValue());
                assertEquals("提交人", sheet.getRow(1).getCell(1).getStringCellValue());
                assertEquals("客资编号", sheet.getRow(0).getCell(3).getStringCellValue());
                assertEquals("KZ202608160000000001", sheet.getRow(1).getCell(3).getStringCellValue());
                assertEquals("测试客资", sheet.getRow(1).getCell(4).getStringCellValue());
            }
        }
    }

    @Test
    void otherProvidersGenerateOnlyContractFields() throws Exception {
        try (var context = providerContext()) {
            SalesOrderListItemRespVO order = new SalesOrderListItemRespVO();
            order.setId(21L);
            order.setOrderNo("SO001");
            when(context.getBean(SalesOrderService.class).getMyPage(any(), eq(7L)))
                    .thenReturn(new PageResult<>(List.of(order), 1L));
            FinanceOrderExportRowRespVO financeOrder = new FinanceOrderExportRowRespVO();
            financeOrder.setOrderNo("FSO001");
            when(context.getBean(SalesOrderService.class).getFinanceExportPage(any(), eq(7L)))
                    .thenReturn(new PageResult<>(List.of(financeOrder), 1L));
            CashbackRespVO cashback = new CashbackRespVO();
            cashback.setId(31L);
            cashback.setCashbackNo("CB001");
            cashback.setAmount(BigDecimal.TEN);
            when(context.getBean(CashbackService.class).getPage(any(), isNull()))
                    .thenReturn(new PageResult<>(List.of(cashback), 1L));
            WithdrawalRespVO withdrawal = new WithdrawalRespVO();
            withdrawal.setId(41L);
            withdrawal.setWithdrawalNo("WD001");
            withdrawal.setMaskedCardNumber("****1234");
            withdrawal.setCardNumber("622200001234");
            when(context.getBean(WithdrawalService.class).getManagementPage(any()))
                    .thenReturn(new PageResult<>(List.of(withdrawal), 1L));

            assertWorkbook(context.getBean(SalesOrderExportTypeProvider.class).generate(task("order")), "订单", "SO001");
            assertWorkbook(context.getBean(FinanceOrderExportTypeProvider.class).generate(task("finance_order")), "财务订单台账", "FSO001");
            assertWorkbook(context.getBean(CashbackExportTypeProvider.class).generate(task("cashback")), "返现", "CB001");
            ExportTypeProvider.ExportResult withdrawalResult = context.getBean(WithdrawalExportTypeProvider.class)
                    .generate(task("withdrawal"));
            assertWorkbook(withdrawalResult, "提现", "WD001");
            try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(withdrawalResult.content()))) {
                String allCells = workbook.getSheet("提现").getRow(1).toString();
                assertFalse(allCells.contains("****1234"));
                assertTrue(allCells.contains("622200001234"));
            }
        }
    }

    @Test
    void withdrawalExportsCompleteSnapshotAndPreservesCardTextAndNumericAmounts() throws Exception {
        try (var context = providerContext()) {
            WithdrawalRespVO row = new WithdrawalRespVO().setId(41L).setWithdrawalNo("WD001")
                    .setCardNumber("0001234567890123456").setMaskedCardNumber("****3456")
                    .setAccountNameSnapshot("测试收款人").setBankNameSnapshot("测试银行")
                    .setBranchNameSnapshot("申请时支行").setApplicantName("测试申请人").setPartnerName("测试合作方")
                    .setApplicationAmount(new BigDecimal("1234.56")).setApprovedAmount(new BigDecimal("1234.56"))
                    .setAvailableBalanceSnapshot(new BigDecimal("2000.00")).setCashbackCount(2)
                    .setStatus("paid").setReviewedByName("测试审核人").setReviewReason("审核意见")
                    .setPaidByName("测试登记人").setPayoutRemark("已线下打款")
                    .setPaidAt(java.time.LocalDateTime.of(2026, 9, 29, 10, 30));
            when(context.getBean(WithdrawalService.class).getManagementPage(any()))
                    .thenReturn(new PageResult<>(List.of(row), 1L));
            var result = context.getBean(WithdrawalExportTypeProvider.class).generate(task("withdrawal"));
            try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(result.content()))) {
                var sheet = workbook.getSheet("提现");
                var header = sheet.getRow(0);
                var data = sheet.getRow(1);
                java.util.Map<String, org.apache.poi.ss.usermodel.Cell> cells = new java.util.HashMap<>();
                header.forEach(cell -> cells.put(cell.getStringCellValue(), data.getCell(cell.getColumnIndex())));
                assertEquals(CellType.STRING, cells.get("银行卡号").getCellType());
                assertEquals(row.getCardNumber(), cells.get("银行卡号").getStringCellValue());
                assertEquals(CellType.NUMERIC, cells.get("申请金额").getCellType());
                assertEquals(1234.56, cells.get("申请金额").getNumericCellValue(), 0.001);
                assertEquals("已打款", cells.get("状态").getStringCellValue());
                assertEquals("申请时支行", cells.get("开户支行").getStringCellValue());
                assertEquals("测试申请人", cells.get("申请人").getStringCellValue());
                assertEquals("测试合作方", cells.get("合作方").getStringCellValue());
                assertEquals("测试审核人", cells.get("审核人").getStringCellValue());
                assertEquals("审核意见", cells.get("审核意见").getStringCellValue());
                assertEquals("测试登记人", cells.get("打款登记人").getStringCellValue());
                assertEquals("已线下打款", cells.get("打款备注").getStringCellValue());
                assertEquals("2026-09-29 10:30:00", cells.get("打款时间").getStringCellValue());
                assertEquals(28, header.getLastCellNum());
            }
            verify(context.getBean(WithdrawalReviewService.class)).enrich(row, 7L);
            verify(context.getBean(WithdrawalService.class), never()).getPage(any(), any());
        }
    }

    @Test
    void withdrawalManagementPermissionIsRequiredBeforeReadingFullAccounts() {
        try (var context = providerContext()) {
            var permission = context.getBean(PermissionApi.class);
            when(permission.hasAnyPermissions(7L, "zsjos:withdrawal:finance-query", "zsjos:withdrawal:admin-query"))
                    .thenReturn(false);
            var provider = context.getBean(WithdrawalExportTypeProvider.class);
            assertThrows(ServiceException.class, () -> provider.checkCreator(7L));
            assertThrows(ServiceException.class, () -> provider.generate(task("withdrawal")));
            verifyNoInteractions(context.getBean(WithdrawalService.class), context.getBean(FinanceTraceService.class));
        }
    }

    @Test
    void withdrawalPreservesFiltersAndExportsAcrossPagesWithoutInventingMissingCards() throws Exception {
        try (var context = providerContext()) {
            var service = context.getBean(WithdrawalService.class);
            var row = new WithdrawalRespVO().setWithdrawalNo("WD-HISTORICAL").setMaskedCardNumber("****1234");
            java.util.List<Integer> pageNumbers = new java.util.ArrayList<>();
            when(service.getManagementPage(any())).thenAnswer(invocation -> {
                WithdrawalPageReqVO request = invocation.getArgument(0);
                pageNumbers.add(request.getPageNo());
                assertEquals(200, request.getPageSize());
                assertEquals("paid", request.getStatus());
                assertEquals("WD", request.getKeyword());
                assertEquals(9L, request.getPartnerId());
                return new PageResult<>(List.of(row), 2L);
            });
            var task = task("withdrawal").setFilterJson("{\"pageNo\":9,\"pageSize\":1,\"status\":\"paid\",\"keyword\":\"WD\",\"partnerId\":9}");
            var result = context.getBean(WithdrawalExportTypeProvider.class).generate(task);
            assertEquals(List.of(1, 2), pageNumbers);
            assertEquals(2, result.rowCount());
            try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(result.content()))) {
                var sheet = workbook.getSheet("提现");
                assertEquals(2, sheet.getLastRowNum());
                assertFalse(sheet.getRow(1).toString().contains("****1234"));
                assertEquals("", new org.apache.poi.ss.usermodel.DataFormatter().formatCellValue(sheet.getRow(1).getCell(11)));
            }
        }
    }

    @Test
    void providerRejectsInvalidFilterBeforeQuerying() {
        try (var context = providerContext()) {
            SalesOrderExportTypeProvider provider = context.getBean(SalesOrderExportTypeProvider.class);

            assertThrows(RuntimeException.class, () -> provider.validateFilter("{\"status\":\"not-a-status\"}"));

            verifyNoInteractions(context.getBean(SalesOrderService.class));
        }
    }

    @Test
    void providerStopsBeforeGeneratingOversizedWorkbook() {
        try (var context = providerContext()) {
            when(context.getBean(CashbackService.class).getPage(any(), isNull()))
                    .thenReturn(new PageResult<>(List.of(), 100_001L));

            ExportTypeProvider.ExportResult result = context.getBean(CashbackExportTypeProvider.class)
                    .generate(task("cashback"));

            assertEquals(100_001L, result.rowCount());
            assertEquals(0, result.content().length);
        }
    }

    private static AnnotationConfigApplicationContext providerContext() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(Validator.class, () -> Validation.buildDefaultValidatorFactory().getValidator());
        context.registerBean(LeadManagementService.class, () -> mock(LeadManagementService.class));
        context.registerBean(SalesOrderService.class, () -> mock(SalesOrderService.class));
        context.getBeanFactory().registerSingleton("permissionService", mock(SalesOrderObjectPermissionService.class));
        context.registerBean(CashbackService.class, () -> mock(CashbackService.class));
        context.registerBean(WithdrawalService.class, () -> mock(WithdrawalService.class));
        context.registerBean(PermissionApi.class, () -> {
            var permission = mock(PermissionApi.class);
            when(permission.hasAnyPermissions(7L, "zsjos:withdrawal:finance-query", "zsjos:withdrawal:admin-query")).thenReturn(true);
            return permission;
        });
        var trace = mock(FinanceTraceService.class);
        when(trace.enrichWithdrawalPage(any())).thenAnswer(invocation -> invocation.getArgument(0));
        context.getBeanFactory().registerSingleton("traceService", trace);
        context.getBeanFactory().registerSingleton("reviewService", mock(WithdrawalReviewService.class));
        context.register(LeadExportTypeProvider.class, SalesOrderExportTypeProvider.class, FinanceOrderExportTypeProvider.class,
                CashbackExportTypeProvider.class, WithdrawalExportTypeProvider.class);
        context.refresh();
        return context;
    }

    private static ExportTaskDO task(String type) {
        ExportTaskDO task = new ExportTaskDO().setTaskNo("EXP001").setExportType(type).setCreatorUserId(7L)
                .setCreatorNameSnapshot("提交人").setFilterJson("{}");
        task.setTenantId(1L);
        return task;
    }

    private static void assertWorkbook(ExportTypeProvider.ExportResult result, String sheetName, String expectedValue)
            throws Exception {
        assertEquals(1L, result.rowCount());
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(result.content()))) {
            assertTrue(workbook.getSheet(sheetName).getRow(1).toString().contains(expectedValue));
        }
    }
}
