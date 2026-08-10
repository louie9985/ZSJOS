package cn.iocoder.yudao.module.zsjos.controller.admin.product.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ZsjosProductPageReqVO extends PageParam {
    private String name;
    private String productRef;
    private Integer status;
    private Long categoryId;
}
