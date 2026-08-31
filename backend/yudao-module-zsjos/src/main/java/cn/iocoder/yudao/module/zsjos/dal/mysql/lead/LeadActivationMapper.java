package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadActivationDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LeadActivationMapper extends BaseMapperX<LeadActivationDO> {
    default LeadActivationDO selectByIdempotencyKey(String key) {
        return selectOne(new LambdaQueryWrapperX<LeadActivationDO>()
                .eq(LeadActivationDO::getIdempotencyKey, key));
    }
}
