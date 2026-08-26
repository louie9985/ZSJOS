package cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_partner_ownership_log")
@Data
@EqualsAndHashCode(callSuper = true)
public class PartnerOwnershipLogDO extends TenantBaseDO {
    @TableId private Long id;
    private Long partnerId;
    private Long previousEmployeeUserId;
    private String previousEmployeeNameSnapshot;
    private Long employeeUserId;
    private String employeeNameSnapshot;
    private String actionType;
    private String reason;
    private Long operatorUserId;
    private LocalDateTime occurredAt;
}
