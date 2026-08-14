package cn.iocoder.yudao.module.zsjos.service.export;

import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.export.ExportTaskController;
import cn.iocoder.yudao.module.zsjos.dal.mysql.export.ExportTaskMapper;
import cn.iocoder.yudao.module.zsjos.service.audit.BusinessAuditService;
import cn.iocoder.yudao.module.zsjos.service.cashback.CashbackService;
import cn.iocoder.yudao.module.zsjos.service.export.provider.CashbackExportTypeProvider;
import cn.iocoder.yudao.module.zsjos.service.export.provider.LeadExportTypeProvider;
import cn.iocoder.yudao.module.zsjos.service.export.provider.SalesOrderExportTypeProvider;
import cn.iocoder.yudao.module.zsjos.service.export.provider.WithdrawalExportTypeProvider;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadManagementService;
import cn.iocoder.yudao.module.zsjos.service.order.SalesOrderService;
import cn.iocoder.yudao.module.zsjos.service.withdrawal.WithdrawalService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class ExportTaskWiringTest {

    @Test
    void controllerAndServiceStartWithProviderCollection() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(Validator.class, () -> Validation.buildDefaultValidatorFactory().getValidator());
            context.registerBean(ExportTaskMapper.class, () -> mock(ExportTaskMapper.class));
            context.registerBean(AdminUserApi.class, () -> mock(AdminUserApi.class));
            context.registerBean(SecurityFrameworkService.class, () -> mock(SecurityFrameworkService.class));
            context.registerBean(FileApi.class, () -> mock(FileApi.class));
            context.registerBean(BusinessAuditService.class, () -> mock(BusinessAuditService.class));
            context.registerBean(LeadManagementService.class, () -> mock(LeadManagementService.class));
            context.registerBean(SalesOrderService.class, () -> mock(SalesOrderService.class));
            context.registerBean(CashbackService.class, () -> mock(CashbackService.class));
            context.registerBean(WithdrawalService.class, () -> mock(WithdrawalService.class));
            context.register(LeadExportTypeProvider.class, SalesOrderExportTypeProvider.class,
                    CashbackExportTypeProvider.class, WithdrawalExportTypeProvider.class,
                    ExportTaskServiceImpl.class, ExportTaskController.class);

            context.refresh();

            assertNotNull(context.getBean(ExportTaskController.class));
            assertSame(context.getBean(ExportTaskServiceImpl.class), context.getBean(ExportTaskService.class));
        }
    }
}
