package cn.iocoder.yudao.module.zsjos.dal.mysql.payment;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.payment.OrderPaymentAllocationDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderPaymentAllocationMapper extends BaseMapperX<OrderPaymentAllocationDO> {
}
