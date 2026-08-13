package cn.iocoder.yudao.module.zsjos.dal.mysql.withdrawal;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.withdrawal.PartnerBankCardDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface PartnerBankCardMapper extends BaseMapperX<PartnerBankCardDO> {
    default List<PartnerBankCardDO> selectByOwner(Long userId) {
        return selectList(new LambdaQueryWrapperX<PartnerBankCardDO>()
                .eq(PartnerBankCardDO::getOwnerUserId, userId)
                .orderByDesc(PartnerBankCardDO::getDefaultCard).orderByDesc(PartnerBankCardDO::getId));
    }
}
