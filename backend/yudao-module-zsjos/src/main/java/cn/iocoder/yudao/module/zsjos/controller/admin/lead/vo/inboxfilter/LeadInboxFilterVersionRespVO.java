package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.inboxfilter;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeadInboxFilterVersionRespVO {
    private Integer versionNo;
    private Long publishedBy;
    private LocalDateTime publishedAt;
}
