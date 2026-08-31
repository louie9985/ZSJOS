package cn.iocoder.yudao.module.zsjos.dal.mysql.payment;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.payment.PaymentRefundDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface PaymentRefundMapper extends BaseMapperX<PaymentRefundDO> {
    default PaymentRefundDO selectByRefundNo(String refundNo) { return selectOne(new LambdaQueryWrapperX<PaymentRefundDO>().eq(PaymentRefundDO::getRefundNo, refundNo).last("LIMIT 1")); }
    default PaymentRefundDO selectByIdempotencyKey(String key) { return selectOne(new LambdaQueryWrapperX<PaymentRefundDO>().eq(PaymentRefundDO::getIdempotencyKey, key).last("LIMIT 1")); }
    default PaymentRefundDO selectActiveByTransaction(Long transactionId) { return selectOne(new LambdaQueryWrapperX<PaymentRefundDO>().eq(PaymentRefundDO::getPaymentTransactionId, transactionId).in(PaymentRefundDO::getStatus, List.of("approval_pending","approved","submitting","accepted","unknown","manual_review")).last("LIMIT 1")); }
    @Select("SELECT * FROM zsjos_payment_refund WHERE status IN ('accepted','unknown') AND deleted=b'0' AND (next_reconcile_at IS NULL OR next_reconcile_at <= NOW()) ORDER BY id LIMIT #{limit} FOR UPDATE SKIP LOCKED")
    List<PaymentRefundDO> selectDueForReconcile(@Param("limit") int limit);
    @Select("SELECT * FROM zsjos_payment_refund WHERE id=#{id} AND deleted=b'0' FOR UPDATE")
    PaymentRefundDO selectByIdForUpdate(@Param("id") Long id);
}
