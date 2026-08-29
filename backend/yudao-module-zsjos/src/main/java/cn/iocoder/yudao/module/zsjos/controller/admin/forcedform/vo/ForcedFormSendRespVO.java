package cn.iocoder.yudao.module.zsjos.controller.admin.forcedform.vo;

import lombok.Data;

@Data
public class ForcedFormSendRespVO {

    private Long batchId;
    private Integer recipientCount;
    private Integer skippedCompletedCount;
    private Integer filteredCount;

}
