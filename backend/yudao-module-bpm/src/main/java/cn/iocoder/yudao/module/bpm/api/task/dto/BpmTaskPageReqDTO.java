package cn.iocoder.yudao.module.bpm.api.task.dto;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class BpmTaskPageReqDTO extends PageParam {

    @NotEmpty
    private String processDefinitionKey;
}
