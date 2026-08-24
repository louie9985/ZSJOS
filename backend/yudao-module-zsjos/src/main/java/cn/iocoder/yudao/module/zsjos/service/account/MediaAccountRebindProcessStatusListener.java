package cn.iocoder.yudao.module.zsjos.service.account;

import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEventListener;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import static cn.iocoder.yudao.module.zsjos.enums.MediaWorkflowConstants.PROCESS_KEY_REBIND;

@Component
public class MediaAccountRebindProcessStatusListener extends BpmProcessInstanceStatusEventListener {

    @Resource
    private MediaAccountService service;

    @Override
    protected String getProcessDefinitionKey() {
        return PROCESS_KEY_REBIND;
    }

    @Override
    protected void onEvent(BpmProcessInstanceStatusEvent event) {
        service.handleRebindProcessResult(event.getId(), event.getStatus(), event.getReason());
    }
}
