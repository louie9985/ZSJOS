package cn.iocoder.yudao.module.eam.dal.mysql.stock;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.eam.dal.dataobject.stock.EamStockHoldingDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface EamStockHoldingMapper extends BaseMapperX<EamStockHoldingDO> {
    default List<EamStockHoldingDO> selectListByEmployeeId(Long employeeId) {
        return selectList(new LambdaQueryWrapperX<EamStockHoldingDO>()
                .eq(EamStockHoldingDO::getEmployeeId, employeeId).orderByDesc(EamStockHoldingDO::getId));
    }
    default EamStockHoldingDO selectOpenByAssetId(Long assetId) {
        return selectOne(new LambdaQueryWrapperX<EamStockHoldingDO>()
                .eq(EamStockHoldingDO::getAssetId, assetId)
                .in(EamStockHoldingDO::getStatus, 0, 1, 2));
    }
}
