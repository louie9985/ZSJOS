package cn.iocoder.yudao.module.system.dal.dataobject.notice;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("system_notice_attachment")
@KeySequence("system_notice_attachment_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class NoticeAttachmentDO extends TenantBaseDO {
    @TableId private Long id;
    private Long noticeId;
    private Long infraFileId;
    private String fileName;
    private String mimeType;
    private Long fileSize;
    private Integer sort;
}
