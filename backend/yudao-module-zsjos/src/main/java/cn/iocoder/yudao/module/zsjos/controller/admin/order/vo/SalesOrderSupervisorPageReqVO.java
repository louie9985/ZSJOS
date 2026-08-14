package cn.iocoder.yudao.module.zsjos.controller.admin.order.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class SalesOrderSupervisorPageReqVO extends PageParam {
    private Boolean handled;
    @Size(max = 100) private String keyword;
}
