package cn.iocoder.yudao.module.eam.service.transfer;

import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEventListener;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class EamTransferProcessStatusListener extends BpmProcessInstanceStatusEventListener {

    @Resource
    private EamTransferService transferService;

    @Override
    protected String getProcessDefinitionKey() {
        return EamTransferServiceImpl.PROCESS_DEFINITION_KEY;
    }

    @Override
    protected void onEvent(BpmProcessInstanceStatusEvent event) {
        String[] parts = event.getBusinessKey().split(":");
        transferService.handleProcessResult(Long.valueOf(parts[1]), event.getStatus(), event.getReason());
    }

}
