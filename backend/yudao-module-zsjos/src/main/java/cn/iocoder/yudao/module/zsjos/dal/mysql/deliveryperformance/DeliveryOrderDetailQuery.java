package cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryperformance;

import cn.iocoder.yudao.module.zsjos.controller.admin.deliveryperformance.vo.DeliveryOrderDetailVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data @EqualsAndHashCode(callSuper = true)
public class DeliveryOrderDetailQuery extends DeliveryPerformanceDetailQuery {
    private DeliveryOrderDetailVO.Query filters;
}
