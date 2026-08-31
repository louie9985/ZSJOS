package cn.iocoder.yudao.module.eam.job;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.quartz.core.handler.JobHandler;
import cn.iocoder.yudao.framework.tenant.core.job.TenantJob;
import cn.iocoder.yudao.module.eam.service.stock.EamStockService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class EamStockAlertJob implements JobHandler {
    @Resource private EamStockService stockService;
    @Override
    @TenantJob
    public String execute(String param) {
        int days = StrUtil.isBlank(param) ? 30 : Integer.parseInt(param);
        int created = stockService.createReminderProjections(days);
        return String.format("EAM 库存提醒：低库存 %s 项，%s 天内到期 %s 项，本次新增提醒 %s 条",
                stockService.scanLowStock(), days, stockService.scanExpiring(days), created);
    }
}
