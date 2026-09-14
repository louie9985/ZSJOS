package cn.iocoder.yudao.module.bpm.api.event;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.context.ApplicationEvent;

/**
 * 流程实例的状态（结果）发生变化的 Event
 *
 * @author 芋道源码
 */
@SuppressWarnings("ALL")
@Data
public class BpmProcessInstanceStatusEvent extends ApplicationEvent {

    /**
     * 流程实例的编号
     */
    @NotNull(message = "流程实例的编号不能为空")
    private String id;
    /**
     * 流程实例的 key
     */
    @NotNull(message = "流程实例的 key 不能为空")
    private String processDefinitionKey;
    /**
     * 流程定义的编号
     */
    @NotNull(message = "流程定义的编号不能为空")
    private String processDefinitionId;
    /**
     * 流程定义的版本
     */
    private Integer processDefinitionVersion;
    /**
     * 流程实例的结果
     */
    @NotNull(message = "流程实例的状态不能为空")
    private Integer status;
    /**
     * 流程实例结束的原因
     */
    private String reason;

    /**
     * 流程实例对应的业务标识
     * 例如说，请假
     */
    private String businessKey;
    /**
     * 稳定事件编号。相同实例与结果的重复投递具有相同编号。
     */
    @NotNull(message = "流程状态事件编号不能为空")
    private String eventKey;

    /** 触发流程的用户快照，可为空（系统触发）。 */
    private Long initiatorUserId;
    private String initiatorNameSnapshot;
    /** 实际执行身份，例如 USER、SYSTEM_BPM。 */
    private String executorType;
    private String executorIdentity;

    public BpmProcessInstanceStatusEvent(Object source) {
        super(source);
    }

}
