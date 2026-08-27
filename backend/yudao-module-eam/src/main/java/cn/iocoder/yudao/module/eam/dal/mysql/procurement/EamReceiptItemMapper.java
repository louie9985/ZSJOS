package cn.iocoder.yudao.module.eam.dal.mysql.procurement;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.eam.dal.dataobject.procurement.EamReceiptItemDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface EamReceiptItemMapper extends BaseMapperX<EamReceiptItemDO> {
    default List<EamReceiptItemDO> selectListByReceiptId(Long receiptId) {
        return selectList(new LambdaQueryWrapperX<EamReceiptItemDO>()
                .eq(EamReceiptItemDO::getReceiptId, receiptId));
    }
    default List<EamReceiptItemDO> selectListByPurchaseItemId(Long purchaseItemId) {
        return selectList(new LambdaQueryWrapperX<EamReceiptItemDO>()
                .eq(EamReceiptItemDO::getPurchaseItemId, purchaseItemId).orderByAsc(EamReceiptItemDO::getId));
    }
}
