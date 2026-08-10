package cn.iocoder.yudao.module.zsjos.dal.dataobject.lead;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_opportunity")
@KeySequence("zsjos_opportunity_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class OpportunityDO extends TenantBaseDO {
    @TableId private Long id;
    private Long personId;
    private String type;
    private Long leadId;
    private String status;
    private Long ownerUserId;
    private String expectedProductSummary;
    private LocalDateTime nextFollowUpAt;
    private LocalDateTime wonAt;
    private LocalDateTime lostAt;
    private String lostReason;
    private Integer version;
}
