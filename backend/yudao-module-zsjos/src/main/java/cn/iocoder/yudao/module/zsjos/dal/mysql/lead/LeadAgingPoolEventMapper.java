package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAgingPoolEventDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LeadAgingPoolEventMapper extends BaseMapperX<LeadAgingPoolEventDO> {
    default LeadAgingPoolEventDO selectByIdempotencyKey(String key) {
        return selectOne(LeadAgingPoolEventDO::getIdempotencyKey, key);
    }
}
