package cn.iocoder.yudao.module.zsjos.dal.mysql.payment;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.payment.PaymentIntentDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PaymentIntentMapper extends BaseMapperX<PaymentIntentDO> {
    default PaymentIntentDO selectByNo(String no) {
        return selectOne(new LambdaQueryWrapperX<PaymentIntentDO>().eq(PaymentIntentDO::getPaymentOrderNo, no).last("LIMIT 1"));
    }

    default PaymentIntentDO selectByNoAndTokenHash(String no, String tokenHash) {
        return selectOne(new LambdaQueryWrapperX<PaymentIntentDO>()
                .eq(PaymentIntentDO::getPaymentOrderNo, no)
                .eq(PaymentIntentDO::getLinkTokenHash, tokenHash)
                .last("LIMIT 1"));
    }

    default PaymentIntentDO selectByReqsn(String reqsn) {
        return selectOne(new LambdaQueryWrapperX<PaymentIntentDO>()
                .eq(PaymentIntentDO::getReqsn, reqsn).last("LIMIT 1"));
    }

    @Select("SELECT * FROM zsjos_payment_order WHERE id=#{id} AND deleted=b'0' FOR UPDATE")
    PaymentIntentDO selectByIdForUpdate(@Param("id") Long id);

    default PaymentIntentDO selectLatestByPurchaseIntent(Long purchaseIntentId) {
        return selectOne(new LambdaQueryWrapperX<PaymentIntentDO>()
                .eq(PaymentIntentDO::getPurchaseIntentId, purchaseIntentId)
                .orderByDesc(PaymentIntentDO::getId).last("LIMIT 1"));
    }
}
