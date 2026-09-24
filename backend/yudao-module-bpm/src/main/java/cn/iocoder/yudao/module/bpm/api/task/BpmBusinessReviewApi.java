package cn.iocoder.yudao.module.bpm.api.task;

import cn.iocoder.yudao.module.bpm.api.task.dto.BpmBusinessReviewState;

/** Internal public boundary; callers must authorize and lock their business record before a decision. */
public interface BpmBusinessReviewApi {
    BpmBusinessReviewState inspect(String instanceId, String definitionKey, String businessKey, String nodeKey);
    void decide(Long userId, String instanceId, String definitionKey, String businessKey,
                String nodeKey, boolean approve, String reason);
}
