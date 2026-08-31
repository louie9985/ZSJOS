package cn.iocoder.yudao.module.zsjos.dal.mysql.withdrawal;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.withdrawal.PartnerBankCardDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

@Mapper
public interface PartnerBankCardMapper extends BaseMapperX<PartnerBankCardDO> {
    default List<PartnerBankCardDO> selectByOwner(Long userId) {
        return selectList(new LambdaQueryWrapperX<PartnerBankCardDO>()
                .eq(PartnerBankCardDO::getOwnerUserId, userId)
                .orderByDesc(PartnerBankCardDO::getDefaultCard).orderByDesc(PartnerBankCardDO::getId));
    }
    default List<PartnerBankCardDO> selectByPartner(Long partnerId) {
        return selectList(new LambdaQueryWrapperX<PartnerBankCardDO>()
                .eq(PartnerBankCardDO::getPartnerId, partnerId)
                .orderByDesc(PartnerBankCardDO::getDefaultCard).orderByDesc(PartnerBankCardDO::getId));
    }

    default PartnerBankCardDO selectByIdAndOwner(Long id, Long userId) {
        return selectOne(new LambdaQueryWrapperX<PartnerBankCardDO>()
                .eq(PartnerBankCardDO::getId, id).eq(PartnerBankCardDO::getOwnerUserId, userId));
    }
    default PartnerBankCardDO selectByIdAndPartner(Long id, Long partnerId) {
        return selectOne(new LambdaQueryWrapperX<PartnerBankCardDO>()
                .eq(PartnerBankCardDO::getId, id).eq(PartnerBankCardDO::getPartnerId, partnerId));
    }

    default PartnerBankCardDO selectByPartnerAndCardNumber(Long partnerId, String cardNumber) {
        return selectOne(new LambdaQueryWrapperX<PartnerBankCardDO>()
                .eq(PartnerBankCardDO::getPartnerId, partnerId)
                .eq(PartnerBankCardDO::getCardNumber, cardNumber));
    }

    default void clearDefaultByOwner(Long userId) {
        update(null, new LambdaUpdateWrapper<PartnerBankCardDO>()
                .eq(PartnerBankCardDO::getOwnerUserId, userId)
                .eq(PartnerBankCardDO::getDefaultCard, true)
                .set(PartnerBankCardDO::getDefaultCard, false));
    }
    default void clearDefaultByPartner(Long partnerId) {
        update(null, new LambdaUpdateWrapper<PartnerBankCardDO>()
                .eq(PartnerBankCardDO::getPartnerId, partnerId)
                .eq(PartnerBankCardDO::getDefaultCard, true)
                .set(PartnerBankCardDO::getDefaultCard, false));
    }
}
