package cn.iocoder.yudao.module.zsjos.service.payment;

import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEventListener;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class PaymentRefundProcessStatusListener extends BpmProcessInstanceStatusEventListener {
    @Resource private PaymentRefundService service;
    @Override protected String getProcessDefinitionKey() { return "zsjos_payment_refund_approval"; }
    @Override protected void onEvent(BpmProcessInstanceStatusEvent event) { service.handleProcessResult(event.getId(), event.getStatus()); }
}
