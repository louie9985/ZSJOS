package cn.iocoder.yudao.module.zsjos.dal.mysql.product;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.product.ZsjosProductSkuDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ZsjosProductSkuMapper extends BaseMapperX<ZsjosProductSkuDO> {
    default List<ZsjosProductSkuDO> selectListBySpuId(Long spuId) {
        return selectList(new LambdaQueryWrapperX<ZsjosProductSkuDO>().eq(ZsjosProductSkuDO::getSpuId, spuId)
                .orderByAsc(ZsjosProductSkuDO::getSort).orderByAsc(ZsjosProductSkuDO::getId));
    }
    default List<ZsjosProductSkuDO> selectEnabledListBySpuIds(Collection<Long> spuIds) {
        if (spuIds == null || spuIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapperX<ZsjosProductSkuDO>().in(ZsjosProductSkuDO::getSpuId, spuIds)
                .eq(ZsjosProductSkuDO::getStatus, 0).orderByAsc(ZsjosProductSkuDO::getSort));
    }
    default ZsjosProductSkuDO selectBySkuRef(String skuRef) { return selectOne(ZsjosProductSkuDO::getSkuRef, skuRef); }
    default Long selectCountBySpuId(Long spuId) { return selectCount(ZsjosProductSkuDO::getSpuId, spuId); }
    default ZsjosProductSkuDO selectBySpuIdAndHash(Long spuId, String hash) {
        return selectOne(new LambdaQueryWrapperX<ZsjosProductSkuDO>().eq(ZsjosProductSkuDO::getSpuId, spuId)
                .eq(ZsjosProductSkuDO::getAttrValuesHash, hash));
    }
}
