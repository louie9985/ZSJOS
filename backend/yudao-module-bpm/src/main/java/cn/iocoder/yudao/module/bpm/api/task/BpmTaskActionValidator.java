package cn.iocoder.yudao.module.bpm.api.task;

import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskActionContext;

/**
 * BPM 任务动作的业务前置校验扩展点。
 *
 * <p>业务模块可校验自己的 businessKey，并忽略不属于自己的流程。</p>
 */
public interface BpmTaskActionValidator {

    String ACTION_APPROVE = "APPROVE";
    String ACTION_REJECT = "REJECT";
    String ACTION_RETURN = "RETURN";
    String ACTION_DELEGATE = "DELEGATE";
    String ACTION_TRANSFER = "TRANSFER";
    String ACTION_ADD_SIGN = "ADD_SIGN";
    String ACTION_DELETE_SIGN = "DELETE_SIGN";
    String ACTION_WITHDRAW = "WITHDRAW";

    void validate(BpmTaskActionContext context);

}
