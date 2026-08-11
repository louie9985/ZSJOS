package cn.iocoder.yudao.module.zsjos.dal.mysql.order;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderItemDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SalesOrderItemMapper extends BaseMapperX<SalesOrderItemDO> {
    default List<SalesOrderItemDO> selectListByOrderId(Long orderId) {
        return selectList(new LambdaQueryWrapperX<SalesOrderItemDO>().eq(SalesOrderItemDO::getOrderId, orderId)
                .orderByAsc(SalesOrderItemDO::getId));
    }
    default void deleteByOrderId(Long orderId) {
        delete(new LambdaQueryWrapperX<SalesOrderItemDO>().eq(SalesOrderItemDO::getOrderId, orderId));
    }
}
