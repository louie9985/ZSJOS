package cn.iocoder.yudao.module.zsjos.dal.mysql.material;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialTypeDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MaterialTypeMapper extends BaseMapperX<MaterialTypeDO> {
    default MaterialTypeDO selectByCode(String code) {
        return selectOne(MaterialTypeDO::getCode, code);
    }

    default List<MaterialTypeDO> selectAll() {
        return selectList(new LambdaQueryWrapperX<MaterialTypeDO>()
                .orderByAsc(MaterialTypeDO::getId));
    }

    @Select("SELECT * FROM zsjos_material_type WHERE id=#{id} AND tenant_id=#{tenantId} AND deleted=b'0' FOR UPDATE")
    MaterialTypeDO selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    default int publishSchema(Long id, Integer expectedVersion, Long schemaVersionId) {
        return update(null, new LambdaUpdateWrapper<MaterialTypeDO>()
                .eq(MaterialTypeDO::getId, id).eq(MaterialTypeDO::getVersion, expectedVersion)
                .set(MaterialTypeDO::getCurrentSchemaVersionId, schemaVersionId)
                .set(MaterialTypeDO::getVersion, expectedVersion + 1));
    }

    default int updateType(MaterialTypeDO type, Integer expectedVersion) {
        type.setVersion(expectedVersion + 1);
        return update(type, new LambdaUpdateWrapper<MaterialTypeDO>()
                .eq(MaterialTypeDO::getId, type.getId())
                .eq(MaterialTypeDO::getVersion, expectedVersion));
    }
}
