package cn.iocoder.yudao.module.system.dal.dataobject.notice;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
import java.time.LocalDateTime;

@TableName("system_notice_share")
@KeySequence("system_notice_share_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class NoticeShareDO extends TenantBaseDO {
    @TableId private Long id;
    private Long noticeId;
    @ToString.Exclude private String token;
    /** JSON of Infra file IDs selected from this notice's existing attachments. */
    private String attachmentIds;
    private Boolean active;
    private Long version;
    private Long openedBy;
    private LocalDateTime openedAt;
    private Long closedBy;
    private LocalDateTime closedAt;
}
