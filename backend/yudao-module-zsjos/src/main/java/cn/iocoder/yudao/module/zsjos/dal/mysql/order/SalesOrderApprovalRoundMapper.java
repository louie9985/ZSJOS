package cn.iocoder.yudao.module.zsjos.dal.mysql.order;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderApprovalRoundDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SalesOrderApprovalRoundMapper extends BaseMapperX<SalesOrderApprovalRoundDO> {
    default SalesOrderApprovalRoundDO selectLatestByOrderId(Long orderId) {
        return selectOne(new LambdaQueryWrapperX<SalesOrderApprovalRoundDO>()
                .eq(SalesOrderApprovalRoundDO::getOrderId, orderId)
                .orderByDesc(SalesOrderApprovalRoundDO::getRoundNo).last("LIMIT 1"));
    }
    default SalesOrderApprovalRoundDO selectByProcessInstanceId(String processInstanceId) {
        return selectOne(SalesOrderApprovalRoundDO::getProcessInstanceId, processInstanceId);
    }
    default SalesOrderApprovalRoundDO selectByIdempotencyKey(String key) {
        return selectOne(SalesOrderApprovalRoundDO::getSubmissionIdempotencyKey, key);
    }
}
