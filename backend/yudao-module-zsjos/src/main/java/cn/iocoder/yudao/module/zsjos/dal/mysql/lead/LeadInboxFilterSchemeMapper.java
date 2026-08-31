package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadInboxFilterSchemeDO;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import java.time.LocalDateTime;

@Mapper
public interface LeadInboxFilterSchemeMapper extends BaseMapperX<LeadInboxFilterSchemeDO> {
    default LeadInboxFilterSchemeDO selectByAudience(String audience) {
        return selectOne(LeadInboxFilterSchemeDO::getAudience, audience);
    }

    default int updatePublished(Long id, Integer expectedVersion, String configJson, Integer newVersion,
                                Long publishedBy, LocalDateTime publishedAt) {
        return update(null, new LambdaUpdateWrapper<LeadInboxFilterSchemeDO>()
                .eq(LeadInboxFilterSchemeDO::getId, id)
                .eq(LeadInboxFilterSchemeDO::getPublishedVersion, expectedVersion)
                .set(LeadInboxFilterSchemeDO::getDraftConfigJson, configJson)
                .set(LeadInboxFilterSchemeDO::getPublishedConfigJson, configJson)
                .set(LeadInboxFilterSchemeDO::getPublishedVersion, newVersion)
                .set(LeadInboxFilterSchemeDO::getPublishedBy, publishedBy)
                .set(LeadInboxFilterSchemeDO::getPublishedAt, publishedAt));
    }
}
