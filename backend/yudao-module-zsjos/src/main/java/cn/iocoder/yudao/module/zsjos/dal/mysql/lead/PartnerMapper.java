package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PartnerDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PartnerMapper extends BaseMapperX<PartnerDO> {
    default PartnerDO selectEnabledByUserId(Long userId) {
        return selectOne(new LambdaQueryWrapperX<PartnerDO>()
                .eq(PartnerDO::getBoundSystemUserId, userId)
                .isNotNull(PartnerDO::getEnabledAt).isNull(PartnerDO::getDisabledAt)
                .orderByDesc(PartnerDO::getId).last("LIMIT 1"));
    }
}
