package cn.iocoder.yudao.module.bpm.api.task;

import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskActionContext;

/** Explicit opt-in for business-authorized, atomic task takeover and decision. No matching policy means deny. */
public interface BpmBusinessReviewPolicy {
    boolean supports(String processDefinitionKey, String taskDefinitionKey);
    void validate(BpmTaskActionContext context);
}
