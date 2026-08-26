package cn.iocoder.yudao.module.zsjos.dal.dataobject.lead;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_subordinate_sales_command")
@KeySequence("zsjos_subordinate_sales_command_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class SubordinateSalesCommandDO extends TenantBaseDO {
    @TableId private Long id;
    private Long operatorUserId;
    private String idempotencyKey;
    private String actionType;
    private String requestFingerprint;
    private String resultJson;
    private Boolean completed;
}
