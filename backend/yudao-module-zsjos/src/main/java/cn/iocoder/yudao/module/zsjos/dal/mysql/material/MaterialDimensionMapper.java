package cn.iocoder.yudao.module.zsjos.dal.mysql.material;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialDimensionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MaterialDimensionMapper extends BaseMapperX<MaterialDimensionDO> {
    default List<MaterialDimensionDO> selectByVersionId(Long versionId) {
        return selectList(new LambdaQueryWrapperX<MaterialDimensionDO>()
                .eq(MaterialDimensionDO::getMaterialVersionId, versionId)
                .orderByAsc(MaterialDimensionDO::getDimensionKey).orderByAsc(MaterialDimensionDO::getId));
    }

    @Delete("DELETE FROM zsjos_material_dimension WHERE material_version_id=#{versionId} AND tenant_id=#{tenantId}")
    int deletePhysicalByVersionId(@Param("versionId") Long versionId, @Param("tenantId") Long tenantId);
}
