package cn.iocoder.yudao.module.eam.dal.mysql.procurement;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.eam.dal.dataobject.procurement.EamPurchaseSourceDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface EamPurchaseSourceMapper extends BaseMapperX<EamPurchaseSourceDO> {
    default List<EamPurchaseSourceDO> selectListByPurchaseItemIds(Collection<Long> itemIds) {
        return itemIds == null || itemIds.isEmpty() ? List.of()
                : selectList(new LambdaQueryWrapperX<EamPurchaseSourceDO>()
                .in(EamPurchaseSourceDO::getPurchaseItemId, itemIds));
    }
    default List<EamPurchaseSourceDO> selectListByPurchaseItemId(Long itemId) {
        return selectList(new LambdaQueryWrapperX<EamPurchaseSourceDO>()
                .eq(EamPurchaseSourceDO::getPurchaseItemId, itemId).orderByAsc(EamPurchaseSourceDO::getId));
    }
}
