package cn.iocoder.yudao.module.zsjos.dal.mysql.production;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.production.ProductionTicketCommandDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductionTicketCommandMapper extends BaseMapperX<ProductionTicketCommandDO> {
    default ProductionTicketCommandDO selectByOperatorAndKey(Long operatorUserId, String idempotencyKey) {
        return selectOne(new LambdaQueryWrapperX<ProductionTicketCommandDO>()
                .eq(ProductionTicketCommandDO::getOperatorUserId, operatorUserId)
                .eq(ProductionTicketCommandDO::getIdempotencyKey, idempotencyKey));
    }

    default int complete(Long operatorUserId, String idempotencyKey, String resultJson) {
        return update(null, new LambdaUpdateWrapper<ProductionTicketCommandDO>()
                .eq(ProductionTicketCommandDO::getOperatorUserId, operatorUserId)
                .eq(ProductionTicketCommandDO::getIdempotencyKey, idempotencyKey)
                .eq(ProductionTicketCommandDO::getCompleted, false)
                .set(ProductionTicketCommandDO::getResultJson, resultJson)
                .set(ProductionTicketCommandDO::getCompleted, true));
    }
}
