package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.dispatch;

import lombok.Data;

@Data
public class SalesDispatchStatusRespVO {
    private Boolean eligible;
    private String presence;
    private String mode;
    private String effectiveStatus;
}
