package cn.iocoder.yudao.module.zsjos.dal.mysql.product;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.ZsjosProductPageReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.product.ZsjosProductDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ZsjosProductMapper extends BaseMapperX<ZsjosProductDO> {
    @Select("SELECT * FROM zsjos_product WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = b'0' FOR UPDATE")
    ZsjosProductDO selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    default PageResult<ZsjosProductDO> selectPage(ZsjosProductPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ZsjosProductDO>()
                .likeIfPresent(ZsjosProductDO::getName, reqVO.getName())
                .likeIfPresent(ZsjosProductDO::getProductRef, reqVO.getProductRef())
                .eqIfPresent(ZsjosProductDO::getCategoryId, reqVO.getCategoryId())
                .eqIfPresent(ZsjosProductDO::getStatus, reqVO.getStatus())
                .orderByAsc(ZsjosProductDO::getSort)
                .orderByDesc(ZsjosProductDO::getId));
    }

    default ZsjosProductDO selectByProductRef(String productRef) {
        return selectOne(ZsjosProductDO::getProductRef, productRef);
    }

    default List<ZsjosProductDO> selectListByRefs(Collection<String> refs) {
        return selectList(new LambdaQueryWrapperX<ZsjosProductDO>()
                .in(ZsjosProductDO::getProductRef, refs));
    }

    default List<ZsjosProductDO> selectEnabledList() {
        return selectList(new LambdaQueryWrapperX<ZsjosProductDO>()
                .eq(ZsjosProductDO::getStatus, 0)
                .orderByAsc(ZsjosProductDO::getSort)
                .orderByDesc(ZsjosProductDO::getId));
    }

    default ZsjosProductDO selectByCategoryIdAndName(Long categoryId, String name) {
        return selectOne(new LambdaQueryWrapperX<ZsjosProductDO>()
                .eq(ZsjosProductDO::getCategoryId, categoryId)
                .eq(ZsjosProductDO::getName, name));
    }

    default Long selectCountByCategoryId(Long categoryId) {
        return selectCount(ZsjosProductDO::getCategoryId, categoryId);
    }
}
