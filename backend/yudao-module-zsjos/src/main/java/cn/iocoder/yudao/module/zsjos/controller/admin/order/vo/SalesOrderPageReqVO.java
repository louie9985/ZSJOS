package cn.iocoder.yudao.module.zsjos.controller.admin.order.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.constraints.Size;

@Data
@EqualsAndHashCode(callSuper = true)
public class SalesOrderPageReqVO extends PageParam {
    @Size(max = 32)
    private String center;
    private Boolean handled;

    @Size(max = 64)
    private String groupKey;

    @Size(max = 64)
    private String optionKey;

    @Size(max = 100)
    private String keyword;
}
