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

    /** null means no filter; an explicitly empty filter must never become an unrestricted query. */
    public BpmTaskPageReqDTO setProcessInstanceIds(List<String> ids) {
        this.processInstanceIds = ids == null ? null : ids.stream()
                .filter(id -> id != null && !id.isBlank()).distinct().toList();
        return this;
    }

    private String processVariableName;

    private List<String> processVariableValues;
}
