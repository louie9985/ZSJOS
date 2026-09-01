package cn.iocoder.yudao.module.system.dal.dataobject.notice;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("system_notice_read")
@KeySequence("system_notice_read_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class NoticeReadDO extends TenantBaseDO {
    @TableId private Long id;
    private Long noticeId;
    private Long userId;
    private LocalDateTime readTime;
}
