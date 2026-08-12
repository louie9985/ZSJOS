package cn.iocoder.yudao.module.zsjos.dal.dataobject.lead;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_lead_follow_up_rule")
@KeySequence("zsjos_lead_follow_up_rule_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class LeadFollowUpRuleDO extends TenantBaseDO {
    @TableId private Long id;
    private String code;
    private String name;
    private Integer firstFollowUpTimeoutMinutes;
    private Integer qualificationTimeoutMinutes;
    private Integer agingPoolTimeoutDays;
    private Integer noProgressWarningDays;
    private Integer noProgressGraceDays;
    private Integer status;
    private Integer version;
}
