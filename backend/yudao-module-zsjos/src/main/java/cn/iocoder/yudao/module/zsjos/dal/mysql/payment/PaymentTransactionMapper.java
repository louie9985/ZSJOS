package cn.iocoder.yudao.module.zsjos.dal.mysql.payment;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.payment.PaymentTransactionDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PaymentTransactionMapper extends BaseMapperX<PaymentTransactionDO> {
    default PaymentTransactionDO selectByPaymentOrderId(Long paymentOrderId) {
        return selectOne(new LambdaQueryWrapperX<PaymentTransactionDO>()
                .eq(PaymentTransactionDO::getPaymentOrderId, paymentOrderId).last("LIMIT 1"));
    }

    default PaymentTransactionDO selectByEvent(String eventId) {
        return selectOne(new LambdaQueryWrapperX<PaymentTransactionDO>()
                .eq(PaymentTransactionDO::getCallbackEventId, eventId).last("LIMIT 1"));
    }
}
