package cn.iocoder.yudao.module.zsjos.dal.mysql.material;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialSchemaVersionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import java.util.List;

@Mapper
public interface MaterialSchemaVersionMapper extends BaseMapperX<MaterialSchemaVersionDO> {

    default List<MaterialSchemaVersionDO> selectListByTypeId(Long materialTypeId) {
        return selectList(new LambdaQueryWrapperX<MaterialSchemaVersionDO>()
                .eq(MaterialSchemaVersionDO::getMaterialTypeId, materialTypeId)
                .orderByDesc(MaterialSchemaVersionDO::getVersionNo));
    }

    default MaterialSchemaVersionDO selectLatestByTypeId(Long materialTypeId) {
        return selectOne(new LambdaQueryWrapperX<MaterialSchemaVersionDO>()
                .eq(MaterialSchemaVersionDO::getMaterialTypeId, materialTypeId)
                .orderByDesc(MaterialSchemaVersionDO::getVersionNo)
                .last("LIMIT 1"));
    }

    @Select("SELECT * FROM zsjos_material_schema_version WHERE material_type_id=#{materialTypeId} "
            + "AND tenant_id=#{tenantId} AND deleted=b'0' ORDER BY version_no DESC LIMIT 1 FOR UPDATE")
    MaterialSchemaVersionDO selectLatestByTypeIdForUpdate(@Param("materialTypeId") Long materialTypeId,
                                                           @Param("tenantId") Long tenantId);

    @Select("SELECT * FROM zsjos_material_schema_version WHERE id=#{id} AND tenant_id=#{tenantId} "
            + "AND deleted=b'0' FOR UPDATE")
    MaterialSchemaVersionDO selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    default int updateDraft(Long id, Integer expectedVersion, String fieldsJson, String schemaHash) {
        return update(null, new LambdaUpdateWrapper<MaterialSchemaVersionDO>()
                .eq(MaterialSchemaVersionDO::getId, id)
                .eq(MaterialSchemaVersionDO::getStatus, "DRAFT")
                .eq(MaterialSchemaVersionDO::getVersion, expectedVersion)
                .set(MaterialSchemaVersionDO::getFieldsJson, fieldsJson)
                .set(MaterialSchemaVersionDO::getSchemaHash, schemaHash)
                .set(MaterialSchemaVersionDO::getVersion, expectedVersion + 1));
    }
}
