package cn.iocoder.yudao.module.zsjos.dal.mysql.withdrawal;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.withdrawal.WithdrawalItemDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface WithdrawalItemMapper extends BaseMapperX<WithdrawalItemDO> {
    default List<WithdrawalItemDO> selectByWithdrawalId(Long withdrawalId) {
        return selectList(WithdrawalItemDO::getWithdrawalId, withdrawalId);
    }
    default void deactivate(Long withdrawalId) {
        update(null, new LambdaUpdateWrapper<WithdrawalItemDO>()
                .eq(WithdrawalItemDO::getWithdrawalId, withdrawalId)
                .eq(WithdrawalItemDO::getActiveFlag, true).set(WithdrawalItemDO::getActiveFlag, false));
    }
}
