package cn.iocoder.yudao.module.zsjos.dal.mysql.payment;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.payment.PurchaseIntentDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PurchaseIntentMapper extends BaseMapperX<PurchaseIntentDO> {
    default PurchaseIntentDO selectActive(Long leadId, Long personId, String purchaseType, String sourceKey, Long userId) {
        return selectOne(new LambdaQueryWrapperX<PurchaseIntentDO>()
                .eqIfPresent(PurchaseIntentDO::getLeadId, leadId)
                .eqIfPresent(PurchaseIntentDO::getPersonId, personId)
                .eq(PurchaseIntentDO::getPurchaseType, purchaseType)
                .eqIfPresent(PurchaseIntentDO::getSourceKey, sourceKey)
                .eq(PurchaseIntentDO::getInitiatorUserId, userId)
                .in(PurchaseIntentDO::getStatus, "draft", "paid_pending_submission")
                .orderByDesc(PurchaseIntentDO::getId).last("LIMIT 1"));
    }

    @Select("SELECT * FROM zsjos_purchase_intent WHERE id=#{id} AND deleted=b'0' FOR UPDATE")
    PurchaseIntentDO selectByIdForUpdate(@Param("id") Long id);

    default int updateDraft(Long id, Integer version, String collectionMode, String draftJson,
                            String itemSnapshotJson, java.math.BigDecimal totalAmount, String idempotencyKey) {
        return update(null, new LambdaUpdateWrapper<PurchaseIntentDO>()
                .eq(PurchaseIntentDO::getId, id).eq(PurchaseIntentDO::getVersion, version)
                .eq(PurchaseIntentDO::getStatus, "draft")
                .set(PurchaseIntentDO::getCollectionMode, collectionMode)
                .set(PurchaseIntentDO::getDraftJson, draftJson)
                .set(PurchaseIntentDO::getItemSnapshotJson, itemSnapshotJson)
                .set(PurchaseIntentDO::getTotalAmount, totalAmount)
                .set(PurchaseIntentDO::getLastIdempotencyKey, idempotencyKey)
                .set(PurchaseIntentDO::getVersion, version + 1));
    }
}
