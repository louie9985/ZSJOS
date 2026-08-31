package cn.iocoder.yudao.module.zsjos.controller.admin.forcedform.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ForcedFormPendingRespVO {

    private Long formId;
    private Long versionId;
    private Long batchId;
    private Long recipientId;
    private String name;
    private String description;
    private Integer version;
    private LocalDateTime sentAt;

}
