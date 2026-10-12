package cn.iocoder.yudao.module.zsjos.service.pointsredemption;
import cn.iocoder.yudao.module.bpm.api.event.*;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
@Component public class PointsProcessStatusListener extends BpmProcessInstanceStatusEventListener {
    @Resource private PointsRequestService service;
    protected String getProcessDefinitionKey() { return PointsApprovalPolicy.PROCESS_KEY; }
    protected void onEvent(BpmProcessInstanceStatusEvent event) { service.processResult(event.getId(), event.getStatus()); }
}
