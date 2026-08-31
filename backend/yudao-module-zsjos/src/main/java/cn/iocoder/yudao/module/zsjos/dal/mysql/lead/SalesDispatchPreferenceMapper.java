package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.SalesDispatchPreferenceDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SalesDispatchPreferenceMapper extends BaseMapperX<SalesDispatchPreferenceDO> {
    default SalesDispatchPreferenceDO selectByUserId(Long userId) {
        return selectOne(SalesDispatchPreferenceDO::getUserId, userId);
    }
}
