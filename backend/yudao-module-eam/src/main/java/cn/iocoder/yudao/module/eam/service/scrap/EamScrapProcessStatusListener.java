package cn.iocoder.yudao.module.eam.service.scrap;

import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEventListener;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class EamScrapProcessStatusListener extends BpmProcessInstanceStatusEventListener {

    @Resource
    private EamScrapService scrapService;

    @Override
    protected String getProcessDefinitionKey() {
        return "eam-scrap";
    }

    @Override
    protected void onEvent(BpmProcessInstanceStatusEvent event) {
        Long id = Long.valueOf(event.getBusinessKey());
        if (BpmProcessInstanceStatusEnum.APPROVE.getStatus().equals(event.getStatus())) {
            scrapService.approveScrap(id);
        } else if (BpmProcessInstanceStatusEnum.REJECT.getStatus().equals(event.getStatus())) {
            scrapService.rejectScrap(id, event.getReason());
        }
    }

}
