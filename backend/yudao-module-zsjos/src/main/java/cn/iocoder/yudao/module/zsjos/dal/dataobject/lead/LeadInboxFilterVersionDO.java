package cn.iocoder.yudao.module.zsjos.dal.dataobject.lead;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_lead_inbox_filter_version")
@KeySequence("zsjos_lead_inbox_filter_version_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class LeadInboxFilterVersionDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long schemeId;
    private Integer versionNo;
    private String configJson;
    private Long publishedBy;
    private LocalDateTime publishedAt;
}
