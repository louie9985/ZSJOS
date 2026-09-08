package cn.iocoder.yudao.module.zsjos.controller.admin.deliveryclass.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ClassTransferPageReqVO extends PageParam {
    private String status;
}
