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
            when(context.getBean(WithdrawalService.class).getPage(any(), isNull()))
                    .thenReturn(new PageResult<>(List.of(withdrawal), 1L));

            assertWorkbook(context.getBean(SalesOrderExportTypeProvider.class).generate(task("order")), "订单", "SO001");
            assertWorkbook(context.getBean(FinanceOrderExportTypeProvider.class).generate(task("finance_order")), "财务订单台账", "FSO001");
            assertWorkbook(context.getBean(CashbackExportTypeProvider.class).generate(task("cashback")), "返现", "CB001");
            ExportTypeProvider.ExportResult withdrawalResult = context.getBean(WithdrawalExportTypeProvider.class)
                    .generate(task("withdrawal"));
            assertWorkbook(withdrawalResult, "提现", "WD001");
            try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(withdrawalResult.content()))) {
                String allCells = workbook.getSheet("提现").getRow(1).toString();
                assertTrue(allCells.contains("****1234"));
                assertFalse(allCells.contains("622200001234"));
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
