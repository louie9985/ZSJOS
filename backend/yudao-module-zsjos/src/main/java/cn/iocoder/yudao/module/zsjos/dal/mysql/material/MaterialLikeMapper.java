package cn.iocoder.yudao.module.zsjos.dal.mysql.material;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialLikeDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MaterialLikeMapper extends BaseMapperX<MaterialLikeDO> {
    default MaterialLikeDO selectByMaterialAndUser(Long materialId, Long userId) {
        return selectOne(new LambdaQueryWrapperX<MaterialLikeDO>()
                .eq(MaterialLikeDO::getMaterialId, materialId).eq(MaterialLikeDO::getUserId, userId));
    }

    @Select("SELECT * FROM zsjos_material_like WHERE material_id=#{materialId} AND user_id=#{userId} "
            + "AND tenant_id=#{tenantId} AND deleted=b'0' FOR UPDATE")
    MaterialLikeDO selectForUpdate(@Param("materialId") Long materialId, @Param("userId") Long userId,
                                   @Param("tenantId") Long tenantId);

    default java.util.List<MaterialLikeDO> selectActiveByUserAndMaterials(Long userId,
                                                                          java.util.Collection<Long> materialIds) {
        if (materialIds == null || materialIds.isEmpty()) return java.util.List.of();
        return selectList(new LambdaQueryWrapperX<MaterialLikeDO>()
                .eq(MaterialLikeDO::getUserId, userId).eq(MaterialLikeDO::getActive, true)
                .in(MaterialLikeDO::getMaterialId, materialIds));
    }
}
