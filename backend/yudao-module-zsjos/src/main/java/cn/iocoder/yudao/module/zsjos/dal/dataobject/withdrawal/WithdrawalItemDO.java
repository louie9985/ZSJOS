package cn.iocoder.yudao.module.zsjos.dal.dataobject.withdrawal;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;

@TableName("zsjos_withdrawal_item")
@Data
@EqualsAndHashCode(callSuper = true)
public class WithdrawalItemDO extends TenantBaseDO {
    @TableId private Long id;
    private Long withdrawalId;
    private Long cashbackId;
    private BigDecimal amountSnapshot;
    private Boolean activeFlag;
}
