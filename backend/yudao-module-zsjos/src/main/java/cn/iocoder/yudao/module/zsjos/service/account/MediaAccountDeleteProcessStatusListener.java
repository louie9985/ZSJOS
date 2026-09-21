package cn.iocoder.yudao.module.zsjos.service.account;

import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEventListener;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import static cn.iocoder.yudao.module.zsjos.enums.MediaWorkflowConstants.PROCESS_KEY_DELETE;

@Component
public class MediaAccountDeleteProcessStatusListener extends BpmProcessInstanceStatusEventListener {
    @Resource private MediaAccountDeleteService service;
    @Override protected String getProcessDefinitionKey() { return PROCESS_KEY_DELETE; }
    @Override protected void onEvent(BpmProcessInstanceStatusEvent event) {
        service.onResult(event);
    }
}
