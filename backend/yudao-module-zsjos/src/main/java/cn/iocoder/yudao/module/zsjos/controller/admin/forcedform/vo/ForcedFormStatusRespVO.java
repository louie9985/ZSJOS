package cn.iocoder.yudao.module.zsjos.controller.admin.forcedform.vo;

import lombok.Data;

@Data
public class ForcedFormStatusRespVO {

    private Integer pendingCount;
    private Long firstPendingFormId;
    private String firstPendingFormName;

}
