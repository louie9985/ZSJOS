package cn.iocoder.yudao.module.eam.dal.mysql.stock;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.eam.dal.dataobject.stock.EamStockBalanceDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface EamStockBalanceMapper extends BaseMapperX<EamStockBalanceDO> {

    default List<EamStockBalanceDO> selectAvailableCandidates(Long categoryId, String unit, String signature,
                                                               Integer managementMode, Integer deliveryMode,
                                                               Integer custodyMode) {
        return selectList(new LambdaQueryWrapperX<EamStockBalanceDO>()
                .eq(EamStockBalanceDO::getCategoryId, categoryId)
                .eq(EamStockBalanceDO::getUnit, unit)
                .eqIfPresent(EamStockBalanceDO::getAttributeSignature, signature)
                .eq(EamStockBalanceDO::getManagementMode, managementMode)
                .eq(EamStockBalanceDO::getDeliveryMode, deliveryMode)
                .eq(EamStockBalanceDO::getCustodyMode, custodyMode)
                .apply("on_hand_quantity - reserved_quantity - frozen_quantity > 0")
                .orderByDesc(EamStockBalanceDO::getOnHandQuantity));
    }

    default EamStockBalanceDO selectByIdForUpdate(Long id) {
        return selectOne(new LambdaQueryWrapperX<EamStockBalanceDO>()
                .eq(EamStockBalanceDO::getId, id).last("FOR UPDATE"));
    }

    default EamStockBalanceDO selectBySignature(Long categoryId, String unit, String signature,
                                                Integer managementMode, Integer deliveryMode,
                                                Integer custodyMode) {
        return selectOne(new LambdaQueryWrapperX<EamStockBalanceDO>()
                .eq(EamStockBalanceDO::getCategoryId, categoryId)
                .eq(EamStockBalanceDO::getUnit, unit)
                .eq(EamStockBalanceDO::getAttributeSignature, signature)
                .eq(EamStockBalanceDO::getManagementMode, managementMode)
                .eq(EamStockBalanceDO::getDeliveryMode, deliveryMode)
                .eq(EamStockBalanceDO::getCustodyMode, custodyMode));
    }

    default List<EamStockBalanceDO> selectLowStockList() {
        return selectList(new LambdaQueryWrapperX<EamStockBalanceDO>()
                .apply("minimum_quantity > 0 AND on_hand_quantity - reserved_quantity - frozen_quantity < minimum_quantity"));
    }

    default List<EamStockBalanceDO> selectExpiringList(LocalDate deadline) {
        return selectList(new LambdaQueryWrapperX<EamStockBalanceDO>()
                .le(EamStockBalanceDO::getNextExpiryDate, deadline));
    }

    @Update("UPDATE eam_stock_balance SET reserved_quantity = reserved_quantity + #{quantity}, version = version + 1 "
            + "WHERE id = #{id} AND on_hand_quantity - reserved_quantity - frozen_quantity >= #{quantity}")
    int reserve(Long id, Integer quantity);

    @Update("UPDATE eam_stock_balance SET reserved_quantity = reserved_quantity - #{quantity}, version = version + 1 "
            + "WHERE id = #{id} AND reserved_quantity >= #{quantity}")
    int release(Long id, Integer quantity);

    @Update("UPDATE eam_stock_balance SET on_hand_quantity = on_hand_quantity + #{quantity}, version = version + 1 WHERE id = #{id}")
    int inbound(Long id, Integer quantity);

    @Update("UPDATE eam_stock_balance SET on_hand_quantity = on_hand_quantity + #{quantity}, "
            + "frozen_quantity = frozen_quantity + #{quantity}, version = version + 1 WHERE id = #{id}")
    int inboundFrozen(Long id, Integer quantity);

    @Update("UPDATE eam_stock_balance SET on_hand_quantity = on_hand_quantity - #{quantity}, version = version + 1 "
            + "WHERE id = #{id} AND on_hand_quantity - reserved_quantity - frozen_quantity >= #{quantity}")
    int outbound(Long id, Integer quantity);

    @Update("UPDATE eam_stock_balance SET on_hand_quantity = on_hand_quantity - #{quantity}, "
            + "reserved_quantity = reserved_quantity - #{quantity}, version = version + 1 "
            + "WHERE id = #{id} AND on_hand_quantity >= #{quantity} AND reserved_quantity >= #{quantity}")
    int consumeReserved(Long id, Integer quantity);
}
