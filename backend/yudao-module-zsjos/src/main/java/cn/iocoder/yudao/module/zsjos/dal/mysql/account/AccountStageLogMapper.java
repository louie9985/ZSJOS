package cn.iocoder.yudao.module.zsjos.dal.mysql.account;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.AccountStageLogDO;
import org.apache.ibatis.annotations.Mapper;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;

@Mapper
public interface AccountStageLogMapper extends BaseMapperX<AccountStageLogDO> {
    default AccountStageLogDO selectByIdempotencyKey(String key) {
        return selectOne(new LambdaQueryWrapperX<AccountStageLogDO>().eq(AccountStageLogDO::getIdempotencyKey, key));
    }

    default PageResult<AccountStageLogDO> selectPageByAccountId(PageParam page, Long accountId) {
        return selectPage(page, new LambdaQueryWrapperX<AccountStageLogDO>()
                .eq(AccountStageLogDO::getAccountId, accountId)
                .orderByDesc(AccountStageLogDO::getJudgedAt).orderByDesc(AccountStageLogDO::getId));
    }
}
