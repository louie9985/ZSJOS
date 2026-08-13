package cn.iocoder.yudao.module.zsjos.dal.mysql.cashback;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.cashback.CashbackDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.cashback.vo.CashbackPageReqVO;

@Mapper
public interface CashbackMapper extends BaseMapperX<CashbackDO> {
    @Select("SELECT * FROM zsjos_cashback WHERE id=#{id} AND tenant_id=#{tenantId} AND deleted=0 FOR UPDATE")
    CashbackDO selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);
    @Select("SELECT * FROM zsjos_cashback WHERE order_id=#{orderId} AND tenant_id=#{tenantId} AND deleted=0 ORDER BY id FOR UPDATE")
    List<CashbackDO> selectByOrderIdForUpdate(@Param("orderId") Long orderId, @Param("tenantId") Long tenantId);
    default CashbackDO selectByBusinessKey(String key) { return selectOne(CashbackDO::getBusinessKey, key); }
    default PageResult<CashbackDO> selectPage(CashbackPageReqVO request, Long beneficiaryUserId) {
        return selectPage(request, new LambdaQueryWrapperX<CashbackDO>()
                .eqIfPresent(CashbackDO::getBeneficiaryUserId, beneficiaryUserId)
                .eqIfPresent(CashbackDO::getType, request.getType())
                .eqIfPresent(CashbackDO::getStatus, request.getStatus())
                .orderByDesc(CashbackDO::getGeneratedAt).orderByDesc(CashbackDO::getId));
    }
    default List<CashbackDO> selectMatured(LocalDateTime now) {
        return selectList(new LambdaQueryWrapperX<CashbackDO>().eq(CashbackDO::getStatus, "pending_settlement")
                .le(CashbackDO::getAvailableAt, now).last("LIMIT 200"));
    }
    default List<CashbackDO> selectAvailableByBeneficiary(Long userId) {
        return selectList(new LambdaQueryWrapperX<CashbackDO>()
                .eq(CashbackDO::getBeneficiaryUserId, userId).eq(CashbackDO::getStatus, "available")
                .orderByAsc(CashbackDO::getId));
    }
    default int transition(Long id, Integer version, String from, String to, LocalDateTime settledAt) {
        return update(null, new LambdaUpdateWrapper<CashbackDO>().eq(CashbackDO::getId, id)
                .eq(CashbackDO::getVersion, version).eq(CashbackDO::getStatus, from)
                .set(CashbackDO::getStatus, to).set(CashbackDO::getSettledAt, settledAt)
                .set(CashbackDO::getVersion, version + 1));
    }
    default int restoreValid(Long id, Integer version, LocalDateTime generatedAt, LocalDateTime availableAt) {
        return update(null, new LambdaUpdateWrapper<CashbackDO>().eq(CashbackDO::getId, id)
                .eq(CashbackDO::getVersion, version).eq(CashbackDO::getType, "valid")
                .eq(CashbackDO::getStatus, "cancelled")
                .set(CashbackDO::getStatus, "pending_settlement")
                .set(CashbackDO::getGeneratedAt, generatedAt).set(CashbackDO::getAvailableAt, availableAt)
                .set(CashbackDO::getCancelledAt, null).set(CashbackDO::getCancelReason, null)
                .set(CashbackDO::getVersion, version + 1));
    }
    default int transitionStatus(Long id, Integer version, String from, String to) {
        return update(null, new LambdaUpdateWrapper<CashbackDO>().eq(CashbackDO::getId, id)
                .eq(CashbackDO::getVersion, version).eq(CashbackDO::getStatus, from)
                .set(CashbackDO::getStatus, to).set(CashbackDO::getVersion, version + 1));
    }
}
