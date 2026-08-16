package cn.iocoder.yudao.module.bpm.api.task.dto;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class BpmTaskPageReqDTO extends PageParam {

    @NotEmpty
    private String processDefinitionKey;

    private String taskDefinitionKey;

    private List<String> processInstanceIds;

    private String processVariableName;

    private List<String> processVariableValues;
}
