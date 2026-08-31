package cn.iocoder.yudao.module.zsjos.dal.dataobject.lead;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_lead_attachment")
@KeySequence("zsjos_lead_attachment_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class LeadAttachmentDO extends TenantBaseDO {
    @TableId private Long id;
    private Long leadId;
    private Long infraFileId;
    private String fileUrl;
    private String originalName;
    private String contentType;
    private Long fileSize;
    private Integer sort;
}
