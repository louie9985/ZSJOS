package cn.iocoder.yudao.module.zsjos.dal.mysql.product;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.product.ZsjosProductCategoryDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ZsjosProductCategoryMapper extends BaseMapperX<ZsjosProductCategoryDO> {
    default ZsjosProductCategoryDO selectByParentIdAndName(Long parentId, String name) {
        return selectOne(new LambdaQueryWrapperX<ZsjosProductCategoryDO>()
                .eq(ZsjosProductCategoryDO::getParentId, parentId)
                .eq(ZsjosProductCategoryDO::getName, name));
    }
    default List<ZsjosProductCategoryDO> selectListByParentId(Long parentId) {
        return selectList(new LambdaQueryWrapperX<ZsjosProductCategoryDO>()
                .eq(ZsjosProductCategoryDO::getParentId, parentId)
                .orderByAsc(ZsjosProductCategoryDO::getSort)
                .orderByAsc(ZsjosProductCategoryDO::getId));
    }
    default List<ZsjosProductCategoryDO> selectListByIds(Collection<Long> ids) {
        return selectList(new LambdaQueryWrapperX<ZsjosProductCategoryDO>()
                .in(ZsjosProductCategoryDO::getId, ids));
    }
    default Long selectCountByParentId(Long parentId) {
        return selectCount(ZsjosProductCategoryDO::getParentId, parentId);
    }
}
