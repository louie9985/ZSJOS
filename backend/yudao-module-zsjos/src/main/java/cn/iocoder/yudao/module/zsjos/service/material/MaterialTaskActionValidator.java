package cn.iocoder.yudao.module.zsjos.service.material;

import cn.iocoder.yudao.module.bpm.api.task.BpmTaskActionValidator;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskActionContext;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class MaterialTaskActionValidator implements BpmTaskActionValidator {
    @Resource private MaterialApprovalService approvals;

    @Override
    public void validate(BpmTaskActionContext context) {
        if (MaterialApprovalContract.isViralProcess(context.getProcessDefinitionKey())) approvals.validateTaskAction(context);
    }
}
