package cn.iocoder.yudao.module.system.dal.mysql.notify;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.system.dal.dataobject.notify.NotifyBusinessOutboxDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface NotifyBusinessOutboxMapper extends BaseMapperX<NotifyBusinessOutboxDO> {
    @TenantIgnore
    @Update("""
            UPDATE system_notify_business_outbox SET payload=#{payload}, lease_until=#{leaseUntil}, update_time=#{now}
            WHERE id=#{id} AND tenant_id=#{tenantId} AND deleted=b'0' AND status='processing'
              AND claim_token=#{claimToken} AND lease_until > #{now}
            """)
    int checkpoint(@Param("id") Long id, @Param("tenantId") Long tenantId, @Param("claimToken") String claimToken,
                   @Param("payload") String payload, @Param("now") LocalDateTime now,
                   @Param("leaseUntil") LocalDateTime leaseUntil);
    @TenantIgnore
    @Select("""
            SELECT * FROM system_notify_business_outbox
            WHERE deleted = b'0' AND next_attempt_at <= #{now}
              AND (status = 'pending' OR (status = 'processing' AND lease_until < #{now}))
            ORDER BY next_attempt_at, id LIMIT #{limit}
            """)
    List<NotifyBusinessOutboxDO> selectDue(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @TenantIgnore
    @Update("""
            UPDATE system_notify_business_outbox
            SET status='processing', lease_until=#{leaseUntil}, claim_token=#{claimToken}, update_time=#{now}
            WHERE id=#{id} AND deleted=b'0' AND next_attempt_at <= #{now}
              AND (status='pending' OR (status='processing' AND lease_until < #{now}))
            """)
    int claim(@Param("id") Long id, @Param("now") LocalDateTime now,
              @Param("leaseUntil") LocalDateTime leaseUntil, @Param("claimToken") String claimToken);

    @TenantIgnore
    @Select("""
            SELECT * FROM system_notify_business_outbox
            WHERE id=#{id} AND deleted=b'0' AND status='processing' AND claim_token=#{claimToken}
            """)
    NotifyBusinessOutboxDO selectClaimed(@Param("id") Long id, @Param("claimToken") String claimToken);

    @TenantIgnore
    @Update("""
            UPDATE system_notify_business_outbox
            SET status=#{row.status}, attempt_count=#{row.attemptCount}, next_attempt_at=#{row.nextAttemptAt},
                lease_until=#{row.leaseUntil}, last_error=#{row.lastError}, succeeded_at=#{row.succeededAt},
                claim_token=#{row.claimToken},
                update_time=#{now}
            WHERE id=#{row.id} AND deleted=b'0' AND status='processing' AND claim_token=#{expectedClaimToken}
            """)
    int updateDeliveryState(@Param("row") NotifyBusinessOutboxDO row,
                            @Param("expectedClaimToken") String expectedClaimToken,
                            @Param("now") LocalDateTime now);

    default NotifyBusinessOutboxDO selectByEventAndRule(Long tenantId, String sourceEventKey, Long targetRuleId) {
        return selectOne(NotifyBusinessOutboxDO::getTenantId, tenantId,
                NotifyBusinessOutboxDO::getSourceEventKey, sourceEventKey,
                NotifyBusinessOutboxDO::getTargetRuleId, targetRuleId);
    }

    @TenantIgnore
    @Delete("""
            DELETE FROM system_notify_business_outbox
            WHERE (status IN ('succeeded','skipped') AND succeeded_at < #{successBefore})
               OR (status='failed' AND update_time < #{failedBefore})
            """)
    int deleteExpired(@Param("successBefore") LocalDateTime successBefore,
                      @Param("failedBefore") LocalDateTime failedBefore);
}
