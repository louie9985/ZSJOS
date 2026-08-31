package cn.iocoder.yudao.module.eam.dal.mysql.procurement;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.eam.dal.dataobject.procurement.EamReceiptDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface EamReceiptMapper extends BaseMapperX<EamReceiptDO> {
    default List<EamReceiptDO> selectListByPurchaseId(Long purchaseId) {
        return selectList(new LambdaQueryWrapperX<EamReceiptDO>()
                .eq(EamReceiptDO::getPurchaseId, purchaseId).orderByDesc(EamReceiptDO::getId));
    }
}
