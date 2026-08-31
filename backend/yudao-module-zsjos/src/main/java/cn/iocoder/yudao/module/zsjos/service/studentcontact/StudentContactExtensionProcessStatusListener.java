package cn.iocoder.yudao.module.zsjos.service.studentcontact;

import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEventListener;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import static cn.iocoder.yudao.module.zsjos.service.studentcontact.StudentContactConstants.PROCESS_EXTENSION;

@Component
public class StudentContactExtensionProcessStatusListener extends BpmProcessInstanceStatusEventListener {

    @Resource
    private StudentContactService service;

    @Override
    protected String getProcessDefinitionKey() {
        return PROCESS_EXTENSION;
    }

    @Override
    protected void onEvent(BpmProcessInstanceStatusEvent event) {
        service.handleExtensionResult(event.getId(), event.getStatus(), event.getReason());
    }
}
