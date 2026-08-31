package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadInboxFilterVersionDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface LeadInboxFilterVersionMapper extends BaseMapperX<LeadInboxFilterVersionDO> {
    default List<LeadInboxFilterVersionDO> selectListBySchemeId(Long schemeId) {
        return selectList(new LambdaQueryWrapperX<LeadInboxFilterVersionDO>()
                .eq(LeadInboxFilterVersionDO::getSchemeId, schemeId)
                .orderByDesc(LeadInboxFilterVersionDO::getVersionNo));
    }

    default LeadInboxFilterVersionDO selectBySchemeIdAndVersion(Long schemeId, Integer versionNo) {
        return selectOne(new LambdaQueryWrapperX<LeadInboxFilterVersionDO>()
                .eq(LeadInboxFilterVersionDO::getSchemeId, schemeId)
                .eq(LeadInboxFilterVersionDO::getVersionNo, versionNo));
    }
}
