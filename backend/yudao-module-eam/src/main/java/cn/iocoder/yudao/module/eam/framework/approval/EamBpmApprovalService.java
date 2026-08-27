package cn.iocoder.yudao.module.eam.framework.approval;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * BPM-backed approval adapter used by all EAM approval records.
 */
@Component("eamBpmApprovalService")
public class EamBpmApprovalService implements EamApprovalService {

    @Resource
    private BpmProcessInstanceApi processInstanceApi;

    @Override
    public String start(String definitionKey, String businessKey, String summary) {
        return start(definitionKey, businessKey, summary, Map.of());
    }

    @Override
    public String start(String definitionKey, String businessKey, String summary, Map<String, Object> variables) {
        BpmProcessInstanceCreateReqDTO request = new BpmProcessInstanceCreateReqDTO();
        request.setProcessDefinitionKey(definitionKey);
        request.setBusinessKey(businessKey);
        java.util.HashMap<String, Object> processVariables = new java.util.HashMap<>(variables);
        processVariables.put("summary", summary);
        request.setVariables(processVariables);
        return processInstanceApi.createProcessInstance(SecurityFrameworkUtils.getLoginUserId(), request);
    }

    @Override
    public boolean approvalRequired() {
        return true;
    }

    @Override
    public void terminate(String processInstanceId, String reason) {
        processInstanceApi.terminateProcessInstanceByBusiness(SecurityFrameworkUtils.getLoginUserId(),
                processInstanceId, "HRM_EMPLOYEE_LIFECYCLE", reason);
    }

}
