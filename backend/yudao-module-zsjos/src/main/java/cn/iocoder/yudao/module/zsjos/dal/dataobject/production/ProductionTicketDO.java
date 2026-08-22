package cn.iocoder.yudao.module.zsjos.dal.dataobject.production;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_production_ticket")
@KeySequence("zsjos_production_ticket_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProductionTicketDO extends TenantBaseDO {
    @TableId private Long id;
    private String ticketNo;
    private Long accountId;
    private Long ownerOperatorUserId;
    private Long assigneeFilmingEditorUserId;
    private Long reviewerUserId;
    private String scriptText;
    private String scriptUrl;
    private String materialRefsJson;
    private String specJson;
    private Integer ticketVersion;
    private LocalDateTime expectedDeliveredAt;
    private LocalDateTime deadlineAt;
    private Integer entitlementQuota;
    private Integer remainingCount;
    private Integer maxRevisionCount;
    private Integer revisionCount;
    private String reworkReasonType;
    private Boolean overEntitlement;
    private String overEntitlementHandling;
    private String overEntitlementProcessInstanceId;
    private String status;
    private Integer version;
}
