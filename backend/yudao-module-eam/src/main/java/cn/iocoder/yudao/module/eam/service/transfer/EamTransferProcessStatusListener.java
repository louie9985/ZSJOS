package cn.iocoder.yudao.module.eam.service.transfer;

import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEventListener;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class EamTransferProcessStatusListener extends BpmProcessInstanceStatusEventListener {

    @Resource
    private EamTransferService transferService;

    @Override
    protected String getProcessDefinitionKey() {
        return "eam-transfer";
    }

    @Override
    protected void onEvent(BpmProcessInstanceStatusEvent event) {
        Long id = Long.valueOf(event.getBusinessKey());
        if (BpmProcessInstanceStatusEnum.APPROVE.getStatus().equals(event.getStatus())) {
            transferService.approveTransfer(id);
        } else if (BpmProcessInstanceStatusEnum.REJECT.getStatus().equals(event.getStatus())) {
            transferService.rejectTransfer(id, event.getReason());
        } else if (BpmProcessInstanceStatusEnum.CANCEL.getStatus().equals(event.getStatus())) {
            transferService.cancelTransfer(id);
        }
    }

}
