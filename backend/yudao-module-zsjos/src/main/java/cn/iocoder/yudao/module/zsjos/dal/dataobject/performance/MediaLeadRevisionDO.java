package cn.iocoder.yudao.module.zsjos.dal.dataobject.performance;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("zsjos_media_lead_target_revision")
public class MediaLeadRevisionDO extends TenantBaseDO {
    @TableId private Long id;
    private Long targetId;
    private String beforeJson;
    private String afterJson;
    private String reason;
    private Long operatorId;
}
