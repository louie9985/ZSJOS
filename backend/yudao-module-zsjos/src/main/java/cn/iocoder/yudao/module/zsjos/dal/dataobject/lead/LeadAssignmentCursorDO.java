package cn.iocoder.yudao.module.zsjos.dal.dataobject.lead;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_lead_assignment_cursor")
@KeySequence("zsjos_lead_assignment_cursor_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class LeadAssignmentCursorDO extends TenantBaseDO {
    @TableId private Long id;
    private Long ruleId;
    private Long lastSalesUserId;
    private Integer version;
}
