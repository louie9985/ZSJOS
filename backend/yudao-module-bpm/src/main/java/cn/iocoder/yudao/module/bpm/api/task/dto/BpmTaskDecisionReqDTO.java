package cn.iocoder.yudao.module.bpm.api.task.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class BpmTaskDecisionReqDTO {

    @NotEmpty
    private String taskId;

    @NotEmpty
    private String reason;

    private List<String> attachments;
}
