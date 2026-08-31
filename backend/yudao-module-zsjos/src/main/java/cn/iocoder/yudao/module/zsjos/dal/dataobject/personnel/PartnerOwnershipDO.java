package cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_partner_ownership")
@Data
@EqualsAndHashCode(callSuper = true)
public class PartnerOwnershipDO extends TenantBaseDO {
    @TableId private Long id;
    private Long partnerId;
    private Long employeeUserId;
    private String employeeNameSnapshot;
    private LocalDateTime assignedAt;
    @Version private Integer version;
}
