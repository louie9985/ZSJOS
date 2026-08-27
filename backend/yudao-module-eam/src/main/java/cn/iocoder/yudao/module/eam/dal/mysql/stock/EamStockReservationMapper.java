package cn.iocoder.yudao.module.eam.dal.mysql.stock;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.eam.dal.dataobject.stock.EamStockReservationDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface EamStockReservationMapper extends BaseMapperX<EamStockReservationDO> {
    default List<EamStockReservationDO> selectListByDemandItemId(Long demandItemId) {
        return selectList(new LambdaQueryWrapperX<EamStockReservationDO>()
                .eq(EamStockReservationDO::getDemandItemId, demandItemId));
    }
    default EamStockReservationDO selectActiveByAssetId(Long assetId) {
        return selectOne(new LambdaQueryWrapperX<EamStockReservationDO>()
                .eq(EamStockReservationDO::getAssetId, assetId).eq(EamStockReservationDO::getStatus, 1));
    }
    default EamStockReservationDO selectByIdForUpdate(Long id) {
        return selectOne(new LambdaQueryWrapperX<EamStockReservationDO>()
                .eq(EamStockReservationDO::getId, id).last("FOR UPDATE"));
    }
}
