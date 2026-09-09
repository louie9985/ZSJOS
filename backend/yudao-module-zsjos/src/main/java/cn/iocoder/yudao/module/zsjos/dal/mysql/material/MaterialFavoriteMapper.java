package cn.iocoder.yudao.module.zsjos.dal.mysql.material;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialFavoriteDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MaterialFavoriteMapper extends BaseMapperX<MaterialFavoriteDO> {
    default MaterialFavoriteDO selectByMaterialAndUser(Long materialId, Long userId) {
        return selectOne(new LambdaQueryWrapperX<MaterialFavoriteDO>()
                .eq(MaterialFavoriteDO::getMaterialId, materialId).eq(MaterialFavoriteDO::getUserId, userId));
    }

    @Select("SELECT * FROM zsjos_material_favorite WHERE material_id=#{materialId} AND user_id=#{userId} "
            + "AND tenant_id=#{tenantId} AND deleted=b'0' FOR UPDATE")
    MaterialFavoriteDO selectForUpdate(@Param("materialId") Long materialId, @Param("userId") Long userId,
                                       @Param("tenantId") Long tenantId);

    default java.util.List<MaterialFavoriteDO> selectActiveByUserAndMaterials(Long userId,
                                                                              java.util.Collection<Long> materialIds) {
        if (materialIds == null || materialIds.isEmpty()) return java.util.List.of();
        return selectList(new LambdaQueryWrapperX<MaterialFavoriteDO>()
                .eq(MaterialFavoriteDO::getUserId, userId).eq(MaterialFavoriteDO::getActive, true)
                .in(MaterialFavoriteDO::getMaterialId, materialIds));
    }
}
