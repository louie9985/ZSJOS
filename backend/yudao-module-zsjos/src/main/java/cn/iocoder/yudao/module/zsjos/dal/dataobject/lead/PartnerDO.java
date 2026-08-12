package cn.iocoder.yudao.module.zsjos.dal.dataobject.lead;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_partner")
@KeySequence("zsjos_partner_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class PartnerDO extends TenantBaseDO {
    @TableId private Long id;
    private String partnerNo;
    private String name;
    private String mobile;
    private String status;
    private Long boundSystemUserId;
    private String channelId;
    private LocalDateTime enabledAt;
    private LocalDateTime disabledAt;
    private Integer version;
}
