package cn.iocoder.yudao.module.zsjos.dal.mysql.workorder;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.workorder.WorkOrderHistoryDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;
@Mapper public interface WorkOrderHistoryMapper extends BaseMapperX<WorkOrderHistoryDO> {
    default WorkOrderHistoryDO selectByOrderAndKey(Long orderId, String key) { return selectOne(new LambdaQueryWrapperX<WorkOrderHistoryDO>().eq(WorkOrderHistoryDO::getWorkOrderId, orderId).eq(WorkOrderHistoryDO::getIdempotencyKey, key)); }
    default List<WorkOrderHistoryDO> selectByOrderId(Long id) { return selectList(new LambdaQueryWrapperX<WorkOrderHistoryDO>().eq(WorkOrderHistoryDO::getWorkOrderId, id).orderByAsc(WorkOrderHistoryDO::getOperatedAt).orderByAsc(WorkOrderHistoryDO::getId)); }
}
