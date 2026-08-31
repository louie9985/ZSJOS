package cn.iocoder.yudao.module.zsjos.dal.dataobject.lead;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_lead_follow_up_image")
@KeySequence("zsjos_lead_follow_up_image_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class LeadFollowUpImageDO extends TenantBaseDO {
    @TableId private Long id;
    private Long followUpRecordId;
    private Long infraFileId;
    private String originalName;
    private String contentType;
    private Long fileSize;
    private Integer sort;
}
