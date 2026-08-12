package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadClaimDailyCounterDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

@Mapper
public interface LeadClaimDailyCounterMapper extends BaseMapperX<LeadClaimDailyCounterDO> {
    @Insert("""
            INSERT INTO zsjos_lead_claim_daily_counter
              (sales_user_id, claim_date, claim_count, creator, create_time, updater, update_time, deleted, tenant_id)
            VALUES (#{salesUserId}, #{claimDate}, 1, '', NOW(), '', NOW(), b'0', #{tenantId})
            ON DUPLICATE KEY UPDATE
              claim_count = IF(claim_count < #{dailyLimit}, claim_count + 1, claim_count),
              update_time = IF(claim_count < #{dailyLimit}, NOW(), update_time)
            """)
    int reserve(@Param("tenantId") Long tenantId, @Param("salesUserId") Long salesUserId,
                @Param("claimDate") LocalDate claimDate, @Param("dailyLimit") int dailyLimit);
}
