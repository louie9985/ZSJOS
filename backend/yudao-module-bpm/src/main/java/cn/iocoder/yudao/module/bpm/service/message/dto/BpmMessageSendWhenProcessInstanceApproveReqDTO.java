package cn.iocoder.yudao.module.bpm.service.message.dto;

import cn.iocoder.yudao.module.bpm.api.task.dto.BpmStartSubjectDTO;
import jakarta.validation.Valid;
import lombok.Data;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * BPM 发送流程实例被通过 Request DTO
 */
@Data
public class BpmMessageSendWhenProcessInstanceApproveReqDTO {

    /**
     * 流程实例的编号
     */
    @NotEmpty(message = "流程实例的编号不能为空")
    private String processInstanceId;
    /**
     * 流程实例的名字
     */
    @NotEmpty(message = "流程实例的名字不能为空")
    private String processInstanceName;
    @Valid
    @NotNull(message = "发起主体不能为空")
    private BpmStartSubjectDTO startSubject;

}
