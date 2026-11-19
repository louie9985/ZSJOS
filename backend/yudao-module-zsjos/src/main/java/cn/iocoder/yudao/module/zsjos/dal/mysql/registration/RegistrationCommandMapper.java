package cn.iocoder.yudao.module.zsjos.dal.mysql.registration;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.RegistrationCommandDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RegistrationCommandMapper extends BaseMapperX<RegistrationCommandDO> {
    default boolean hasTransferByOperator(Long caseId, Long userId) {
        return exists(new LambdaQueryWrapperX<RegistrationCommandDO>()
                .eq(RegistrationCommandDO::getRegistrationCaseId, caseId)
                .eq(RegistrationCommandDO::getOperatorUserId, userId)
                .eq(RegistrationCommandDO::getCommandType, "transfer"));
    }

    default RegistrationCommandDO selectByIdempotencyKey(String idempotencyKey) {
        return selectOne(new LambdaQueryWrapperX<RegistrationCommandDO>()
                .eq(RegistrationCommandDO::getIdempotencyKey, idempotencyKey));
    }
}
