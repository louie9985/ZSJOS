package cn.iocoder.yudao.module.zsjos.dal.mysql.product;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.product.ZsjosProductAttrDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ZsjosProductAttrMapper extends BaseMapperX<ZsjosProductAttrDO> {
    default List<ZsjosProductAttrDO> selectListBySpuIds(java.util.Collection<Long> ids) {
        if (ids.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapperX<ZsjosProductAttrDO>().in(ZsjosProductAttrDO::getSpuId, ids)
                .orderByAsc(ZsjosProductAttrDO::getSort).orderByAsc(ZsjosProductAttrDO::getId));
    }
    default List<ZsjosProductAttrDO> selectListBySpuId(Long spuId) {
        return selectList(new LambdaQueryWrapperX<ZsjosProductAttrDO>().eq(ZsjosProductAttrDO::getSpuId, spuId)
                .orderByAsc(ZsjosProductAttrDO::getSort).orderByAsc(ZsjosProductAttrDO::getId));
    }
}
