package cn.iocoder.yudao.module.zsjos.dal.mysql.material;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialFieldIndexDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MaterialFieldIndexMapper extends BaseMapperX<MaterialFieldIndexDO> {
    default List<MaterialFieldIndexDO> selectByVersionId(Long versionId) {
        return selectList(MaterialFieldIndexDO::getMaterialVersionId, versionId);
    }

    @Delete("DELETE FROM zsjos_material_field_index WHERE material_version_id=#{versionId} AND tenant_id=#{tenantId}")
    int deletePhysicalByVersionId(@Param("versionId") Long versionId, @Param("tenantId") Long tenantId);
}
