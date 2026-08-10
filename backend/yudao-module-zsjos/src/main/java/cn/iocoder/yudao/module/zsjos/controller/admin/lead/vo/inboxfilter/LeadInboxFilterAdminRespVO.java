package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.inboxfilter;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeadInboxFilterAdminRespVO {
    private String audience;
    private String audienceLabel;
    private List<LeadInboxFilterConfigVO.GroupVO> draftGroups;
    private List<LeadInboxFilterConfigVO.GroupVO> publishedGroups;
    private Integer publishedVersion;
    private LocalDateTime publishedAt;
    private LocalDateTime updateTime;
}
