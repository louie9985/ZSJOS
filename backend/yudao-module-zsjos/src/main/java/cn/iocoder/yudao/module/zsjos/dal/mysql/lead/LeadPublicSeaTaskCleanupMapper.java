package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface LeadPublicSeaTaskCleanupMapper {
    @SelectProvider(type=LeadPublicSeaTaskCleanupSql.class, method="candidates")
    List<Long> selectCandidates(@Param("tenantId") Long tenantId, @Param("leadId") Long leadId);

    @UpdateProvider(type=LeadPublicSeaTaskCleanupSql.class, method="cancel")
    int cancelObsolete(@Param("tenantId") Long tenantId, @Param("ids") List<Long> ids,
                       @Param("now") LocalDateTime now, @Param("reason") String reason);
}
