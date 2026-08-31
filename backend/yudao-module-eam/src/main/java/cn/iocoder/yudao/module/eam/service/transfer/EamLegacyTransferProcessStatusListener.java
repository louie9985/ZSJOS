package cn.iocoder.yudao.module.eam.service.transfer;

import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEventListener;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/** Keeps status delivery working for already-running instances of the retired process key. */
@Component
public class EamLegacyTransferProcessStatusListener extends BpmProcessInstanceStatusEventListener {
    @Resource private EamTransferService transferService;
    @Override protected String getProcessDefinitionKey() { return "eam-transfer"; }
    @Override protected void onEvent(BpmProcessInstanceStatusEvent event) {
        transferService.handleProcessResult(Long.valueOf(event.getBusinessKey()), event.getStatus(), event.getReason());
    }
}
