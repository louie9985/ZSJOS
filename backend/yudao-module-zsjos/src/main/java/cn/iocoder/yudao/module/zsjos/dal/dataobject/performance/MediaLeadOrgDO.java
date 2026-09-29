package cn.iocoder.yudao.module.zsjos.dal.dataobject.performance;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("zsjos_media_lead_org")
public class MediaLeadOrgDO extends TenantBaseDO {
    @TableId private Long id;
    private Long deptId;
    private Long centerId;
    private String kind;
    private Integer version;
}
