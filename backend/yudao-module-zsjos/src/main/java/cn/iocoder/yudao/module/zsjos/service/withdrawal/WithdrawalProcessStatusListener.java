package cn.iocoder.yudao.module.zsjos.service.withdrawal;

import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEventListener;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import static cn.iocoder.yudao.module.zsjos.enums.WithdrawalConstants.PROCESS_DEFINITION_KEY;

@Component
public class WithdrawalProcessStatusListener extends BpmProcessInstanceStatusEventListener {
    @Resource private WithdrawalService service;
    @Override protected String getProcessDefinitionKey() { return PROCESS_DEFINITION_KEY; }
    @Override protected void onEvent(BpmProcessInstanceStatusEvent event) {
        service.handleProcessResult(event.getId(), event.getStatus(), event.getReason());
    }
}
