package cn.iocoder.yudao.module.zsjos.service.deliveryclass;

import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEventListener;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class ClassTransferProcessStatusListener extends BpmProcessInstanceStatusEventListener {
    @Resource private ClassTransferService service;
    @Override protected String getProcessDefinitionKey() { return ClassTransferServiceImpl.PROCESS_DEFINITION_KEY; }
    @Override protected void onEvent(BpmProcessInstanceStatusEvent event) {
        service.handleProcessResult(event.getId(), event.getStatus(), event.getReason());
    }
}
