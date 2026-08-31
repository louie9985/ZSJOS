package cn.iocoder.yudao.module.zsjos.dal.dataobject.withdrawal;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_partner_bank_card")
@Data
@EqualsAndHashCode(callSuper = true)
public class PartnerBankCardDO extends TenantBaseDO {
    @TableId private Long id;
    private Long partnerId;
    private Long ownerUserId;
    private String accountName;
    private String cardNumber;
    private String bankName;
    private String branchName;
    private Boolean defaultCard;
    private Integer version;
}
