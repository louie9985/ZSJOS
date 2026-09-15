package cn.iocoder.yudao.module.zsjos.dal.mysql.partner;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.partner.PartnerLeaderboardConfigDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PartnerLeaderboardConfigMapper extends BaseMapperX<PartnerLeaderboardConfigDO> {
    default PartnerLeaderboardConfigDO selectCurrent() {
        return selectOne(PartnerLeaderboardConfigDO::getDeleted, false);
    }
}
