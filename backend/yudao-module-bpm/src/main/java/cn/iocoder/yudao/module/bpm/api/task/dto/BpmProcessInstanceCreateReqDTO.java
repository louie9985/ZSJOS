package cn.iocoder.yudao.module.bpm.api.task.dto;

import lombok.Data;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/**
 * 流程实例的创建 Request DTO
 *
 * @author 芋道源码
 */
@Data
public class BpmProcessInstanceCreateReqDTO {

    /**
     * 流程定义的编号。业务已冻结具体定义版本时传入，避免按 key 启动到更新版本。
     */
    private String processDefinitionId;

    /**
     * 流程定义的标识
     */
    @NotEmpty(message = "流程定义的标识不能为空")
    private String processDefinitionKey;
    /**
     * 变量实例（动态表单）
     */
    private Map<String, Object> variables;

    /**
     * 业务的唯一标识
     *
     * 例如说，请假申请的编号。通过它，可以查询到对应的实例
     */
    @NotEmpty(message = "业务的唯一标识")
    private String businessKey;

    /**
     * 业务方预生成的流程实例编号。需要在启动前冻结业务状态时使用。
     */
    @Size(max = 64, message = "预生成的流程实例编号长度不能超过 64 个字符")
    @Pattern(regexp = "[A-Za-z0-9_-]+", message = "预生成的流程实例编号格式不正确")
    private String predefinedProcessInstanceId;

    /**
     * 发起人自选审批人 Map
     *
     * key：taskKey 任务编码
     * value：审批人的数组
     * 例如：{ taskKey1 :[1, 2] }，则表示 taskKey1 这个任务，提前设定了，由 userId 为 1,2 的用户进行审批
     */
    private Map<String, List<Long>> startUserSelectAssignees;

}
