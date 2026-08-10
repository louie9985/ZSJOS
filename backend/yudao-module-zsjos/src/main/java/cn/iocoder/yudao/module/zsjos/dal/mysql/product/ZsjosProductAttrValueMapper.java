package cn.iocoder.yudao.module.zsjos.dal.mysql.product;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.product.ZsjosProductAttrValueDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ZsjosProductAttrValueMapper extends BaseMapperX<ZsjosProductAttrValueDO> {
    default List<ZsjosProductAttrValueDO> selectListByAttrIds(Collection<Long> attrIds) {
        if (attrIds == null || attrIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapperX<ZsjosProductAttrValueDO>().in(ZsjosProductAttrValueDO::getAttrId, attrIds)
                .orderByAsc(ZsjosProductAttrValueDO::getSort).orderByAsc(ZsjosProductAttrValueDO::getId));
    }
}
