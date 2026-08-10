package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadFollowUpRuleDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LeadFollowUpRuleMapper extends BaseMapperX<LeadFollowUpRuleDO> {
    default LeadFollowUpRuleDO selectByCode(String code) {
        return selectOne(new LambdaQueryWrapperX<LeadFollowUpRuleDO>()
                .eq(LeadFollowUpRuleDO::getCode, code));
    }
}
