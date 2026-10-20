package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import lombok.Data;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface LeadCalendarMapper {

    @SelectProvider(type = LeadCalendarSearchSql.class, method = "page")
    List<DueLead> searchPage(@Param("req") cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.calendar.LeadCalendarSearchReqVO req,
            @Param("tenantId") Long tenantId, @Param("userId") Long userId, @Param("offset") long offset);
    @SelectProvider(type = LeadCalendarSearchSql.class, method = "count")
    long searchCount(@Param("req") cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.calendar.LeadCalendarSearchReqVO req,
            @Param("tenantId") Long tenantId, @Param("userId") Long userId);

    @Data
    class DueLead {
        private Long id;
        private String category;
        private LocalDateTime deadline;
        private LocalDateTime ownershipStartedAt;
    }

    @SelectProvider(type = LeadCalendarSearchSql.class, method = "due")
    List<DueLead> selectDueLeads(@Param("tenantId") Long tenantId, @Param("userId") Long userId,
                               @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
