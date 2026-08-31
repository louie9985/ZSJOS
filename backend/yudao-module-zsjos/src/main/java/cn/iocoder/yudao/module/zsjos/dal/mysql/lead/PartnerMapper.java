package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PartnerDO;
import org.apache.ibatis.annotations.Mapper;

import static cn.iocoder.yudao.module.zsjos.enums.PersonnelConstants.PARTNER_STATUS_ENABLED;

import java.util.Collection;
import java.util.List;

@Mapper
public interface PartnerMapper extends BaseMapperX<PartnerDO> {
    default List<PartnerDO> selectListByIds(Collection<Long> ids) {
        return selectList(new LambdaQueryWrapperX<PartnerDO>()
                .in(PartnerDO::getId, ids));
    }

    default PartnerDO selectByBoundUserId(Long userId) {
        return selectOne(new LambdaQueryWrapperX<PartnerDO>()
                .eq(PartnerDO::getBoundSystemUserId, userId).last("LIMIT 1"));
    }

    default PartnerDO selectByPartnerNo(String partnerNo) {
        return selectOne(PartnerDO::getPartnerNo, partnerNo);
    }
    default PartnerDO selectEnabledByUserId(Long userId) {
        return selectOne(new LambdaQueryWrapperX<PartnerDO>()
                .eq(PartnerDO::getBoundSystemUserId, userId)
                .eq(PartnerDO::getStatus, PARTNER_STATUS_ENABLED)
                .isNotNull(PartnerDO::getEnabledAt).isNull(PartnerDO::getDisabledAt)
                .orderByDesc(PartnerDO::getId).last("LIMIT 1"));
    }
}
