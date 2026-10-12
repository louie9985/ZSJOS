package cn.iocoder.yudao.module.zsjos.dal.mysql.performance;

import cn.iocoder.yudao.module.zsjos.controller.admin.performance.vo.OrderDetailVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data @EqualsAndHashCode(callSuper = true)
public class OrderDetailQuery extends PerformanceDetailQuery {
    private OrderDetailVO.Query filters;
}
