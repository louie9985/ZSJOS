package cn.iocoder.yudao.module.system.dal.dataobject.notice;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("system_notice_recipient")
@KeySequence("system_notice_recipient_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class NoticeRecipientDO extends TenantBaseDO {
    @TableId private Long id;
    private Long noticeId;
    private Long userId;
}
