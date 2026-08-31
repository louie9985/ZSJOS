package cn.iocoder.yudao.module.eam.service.employeeasset;

import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEventListener;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import static cn.iocoder.yudao.module.eam.enums.procurement.EamProcurementConstants.EMPLOYEE_REVIEW_PROCESS_KEY;

@Component
public class EamEmployeeReviewProcessStatusListener extends BpmProcessInstanceStatusEventListener {
    @Resource private EamEmployeeAssetService employeeAssetService;
    @Override protected String getProcessDefinitionKey() { return EMPLOYEE_REVIEW_PROCESS_KEY; }
    @Override protected void onEvent(BpmProcessInstanceStatusEvent event) {
        employeeAssetService.handleReviewProcessResult(Long.valueOf(event.getBusinessKey()), event.getStatus(), event.getReason());
    }
}
