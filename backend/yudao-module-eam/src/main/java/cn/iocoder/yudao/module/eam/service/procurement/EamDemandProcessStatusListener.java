package cn.iocoder.yudao.module.eam.service.procurement;

import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEventListener;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import static cn.iocoder.yudao.module.eam.enums.procurement.EamProcurementConstants.DEMAND_PROCESS_KEY;

@Component
public class EamDemandProcessStatusListener extends BpmProcessInstanceStatusEventListener {
    @Resource private EamDemandService demandService;
    @Override protected String getProcessDefinitionKey() { return DEMAND_PROCESS_KEY; }
    @Override protected void onEvent(BpmProcessInstanceStatusEvent event) {
        demandService.handleProcessResult(Long.valueOf(event.getBusinessKey()), event.getStatus(), event.getReason());
    }
}
