package cn.iocoder.yudao.module.zsjos.dal.mysql.account;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.AccountStageLogDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AccountStageLogMapper extends BaseMapperX<AccountStageLogDO> {
    default AccountStageLogDO selectByIdempotencyKey(String key) {
        return selectOne(new LambdaQueryWrapperX<AccountStageLogDO>().eq(AccountStageLogDO::getIdempotencyKey, key));
    }
}
