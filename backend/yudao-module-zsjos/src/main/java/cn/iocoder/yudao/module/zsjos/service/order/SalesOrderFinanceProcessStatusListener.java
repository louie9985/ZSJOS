package cn.iocoder.yudao.module.zsjos.service.order;

import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEventListener;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import static cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.FINANCE_PROCESS_DEFINITION_KEY;

@Component
public class SalesOrderFinanceProcessStatusListener extends BpmProcessInstanceStatusEventListener {
    @Resource private SalesOrderService salesOrderService;

    @Override protected String getProcessDefinitionKey() { return FINANCE_PROCESS_DEFINITION_KEY; }

    @Override protected void onEvent(BpmProcessInstanceStatusEvent event) {
        salesOrderService.handleProcessResult(event.getId(), event.getStatus(), event.getReason());
    }
}
