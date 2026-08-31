package cn.iocoder.yudao.module.zsjos.controller.admin.forcedform.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ForcedFormRespVO {

    private Long id;
    private Long currentVersionId;
    private String name;
    private String description;
    private String fieldsJson;
    private String status;
    private Integer version;
    private Integer recipientCount;
    private Integer completedCount;
    private Integer pendingCount;
    private LocalDateTime lastSentAt;

}
