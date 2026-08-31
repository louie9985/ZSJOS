package cn.iocoder.yudao.module.eam.dal.mysql.procurement;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.eam.dal.dataobject.procurement.EamPurchaseItemDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface EamPurchaseItemMapper extends BaseMapperX<EamPurchaseItemDO> {
    default List<EamPurchaseItemDO> selectListByPurchaseId(Long purchaseId) {
        return selectList(new LambdaQueryWrapperX<EamPurchaseItemDO>()
                .eq(EamPurchaseItemDO::getPurchaseId, purchaseId).orderByAsc(EamPurchaseItemDO::getId));
    }
    default EamPurchaseItemDO selectByIdForUpdate(Long id) {
        return selectOne(new LambdaQueryWrapperX<EamPurchaseItemDO>()
                .eq(EamPurchaseItemDO::getId, id).last("FOR UPDATE"));
    }
}
