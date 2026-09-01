package cn.iocoder.yudao.module.zsjos.service.personnel;

import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEventListener;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import static cn.iocoder.yudao.module.zsjos.enums.PersonnelConstants.PARTNER_OPEN_REQUEST_PROCESS_DEFINITION_KEY;

@Component
public class PartnerOpenRequestProcessStatusListener extends BpmProcessInstanceStatusEventListener {

    @Resource private PartnerOpenRequestService service;

    @Override
    protected String getProcessDefinitionKey() {
        return PARTNER_OPEN_REQUEST_PROCESS_DEFINITION_KEY;
    }

    @Override
    protected void onEvent(BpmProcessInstanceStatusEvent event) {
        service.handleProcessResult(event.getId(), event.getStatus(), event.getReason());
    }
}
