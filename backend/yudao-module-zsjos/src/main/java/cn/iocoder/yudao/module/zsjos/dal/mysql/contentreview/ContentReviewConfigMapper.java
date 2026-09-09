package cn.iocoder.yudao.module.zsjos.dal.mysql.contentreview;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.contentreview.ContentReviewConfigDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ContentReviewConfigMapper extends BaseMapperX<ContentReviewConfigDO> {

    default ContentReviewConfigDO selectCurrent() {
        return selectOne(new LambdaQueryWrapperX<ContentReviewConfigDO>().last("LIMIT 1"));
    }

    @Select("SELECT * FROM zsjos_content_review_config WHERE tenant_id=#{tenantId} AND deleted=b'0' LIMIT 1 FOR UPDATE")
    ContentReviewConfigDO selectCurrentForUpdate(@Param("tenantId") Long tenantId);

    default int updateConfig(ContentReviewConfigDO row, Integer expectedVersion) {
        return update(null, new LambdaUpdateWrapper<ContentReviewConfigDO>()
                .eq(ContentReviewConfigDO::getId, row.getId())
                .eq(ContentReviewConfigDO::getVersion, expectedVersion)
                .set(ContentReviewConfigDO::getProcessDefinitionKey, row.getProcessDefinitionKey())
                .set(ContentReviewConfigDO::getDirectorTaskKey, row.getDirectorTaskKey())
                .set(ContentReviewConfigDO::getFinalTaskKey, row.getFinalTaskKey())
                .set(ContentReviewConfigDO::getProductionMaterialTypeCode, row.getProductionMaterialTypeCode())
                .set(ContentReviewConfigDO::getMaterialFieldMappingJson, row.getMaterialFieldMappingJson())
                .set(ContentReviewConfigDO::getMaterialDefaultValuesJson, row.getMaterialDefaultValuesJson())
                .set(ContentReviewConfigDO::getVersion, expectedVersion + 1));
    }
}
