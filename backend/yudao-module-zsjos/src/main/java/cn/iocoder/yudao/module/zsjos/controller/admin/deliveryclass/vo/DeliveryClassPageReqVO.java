package cn.iocoder.yudao.module.zsjos.controller.admin.deliveryclass.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DeliveryClassPageReqVO extends PageParam {
    private String status;
    private String keyword;
    private Long categoryId;
    private Long examScheduleId;
    private Long homeroomUserId;
}
