package cn.iocoder.yudao.module.zsjos.controller.admin.workorder.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class WorkOrderMyPageReqVO extends PageParam {
    @jakarta.validation.constraints.Pattern(regexp = "SELF|ALL|USER")
    private String readScope;
    private Long targetUserId;
    @Size(max = 40)
    private String status;
    @Size(max = 32)
    private String view;
}
