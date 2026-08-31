package cn.iocoder.yudao.module.zsjos.service.order;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskDisplay;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskSceneProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.BIZ_TYPE_SALES_ORDER;
import static cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.TASK_ACTION_REVISION;
import static cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.TASK_TYPE_REVISION;

@Component
public class SalesOrderBusinessTaskSceneProvider implements BusinessTaskSceneProvider {

    @Resource
    private SalesOrderMapper orderMapper;

    @Override
    public String getBizType() {
        return BIZ_TYPE_SALES_ORDER;
    }

    @Override
    public Map<Long, BusinessTaskDisplay> getDisplayMap(List<BusinessTaskDO> tasks) {
        Map<Long, SalesOrderDO> orders = orderMapper.selectBatchIds(tasks.stream()
                        .map(BusinessTaskDO::getBizId).distinct().toList()).stream()
                .collect(Collectors.toMap(SalesOrderDO::getId, Function.identity()));
        Map<Long, BusinessTaskDisplay> result = new HashMap<>();
        for (BusinessTaskDO task : tasks) {
            SalesOrderDO order = orders.get(task.getBizId());
            String orderNo = order == null ? String.valueOf(task.getBizId()) : order.getOrderNo();
            String studentName = order == null ? null : order.getStudentName();
            result.put(task.getId(), new BusinessTaskDisplay(
                    TASK_TYPE_REVISION.equals(task.getTaskType()) ? "补正成交订单：" + orderNo : "成交订单：" + orderNo,
                    studentName == null ? null : "学员：" + studentName,
                    TASK_TYPE_REVISION.equals(task.getTaskType()) ? TASK_ACTION_REVISION : null));
        }
        return result;
    }
}
