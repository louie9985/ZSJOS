package cn.iocoder.yudao.module.eam.service.procurement;

import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEventListener;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import static cn.iocoder.yudao.module.eam.enums.procurement.EamProcurementConstants.EXPENSE_PROCESS_KEY;

@Component
public class EamExpenseProcessStatusListener extends BpmProcessInstanceStatusEventListener {
    @Resource private EamPurchaseService purchaseService;
    @Override protected String getProcessDefinitionKey() { return EXPENSE_PROCESS_KEY; }
    @Override protected void onEvent(BpmProcessInstanceStatusEvent event) {
        purchaseService.handleExpenseProcessResult(Long.valueOf(event.getBusinessKey()), event.getStatus(), event.getReason());
    }
}
