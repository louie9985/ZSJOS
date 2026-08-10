package cn.iocoder.yudao.module.zsjos.dal.dataobject.lead;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_lead_inbox_filter_scheme")
@KeySequence("zsjos_lead_inbox_filter_scheme_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class LeadInboxFilterSchemeDO extends TenantBaseDO {
    @TableId
    private Long id;
    private String audience;
    private String name;
    private String draftConfigJson;
    private String publishedConfigJson;
    private Integer publishedVersion;
    private Long publishedBy;
    private LocalDateTime publishedAt;
    private Integer version;
}
