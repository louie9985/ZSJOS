package cn.iocoder.yudao.module.zsjos.dal.mysql.material;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialFileDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MaterialFileMapper extends BaseMapperX<MaterialFileDO> {
    default List<MaterialFileDO> selectByVersionId(Long versionId) {
        return selectList(new LambdaQueryWrapperX<MaterialFileDO>()
                .eq(MaterialFileDO::getMaterialVersionId, versionId)
                .orderByAsc(MaterialFileDO::getFieldKey).orderByAsc(MaterialFileDO::getGroupIndex)
                .orderByAsc(MaterialFileDO::getId));
    }

    @Delete("DELETE FROM zsjos_material_file WHERE material_version_id=#{versionId} AND tenant_id=#{tenantId}")
    int deletePhysicalByVersionId(@Param("versionId") Long versionId, @Param("tenantId") Long tenantId);
}
