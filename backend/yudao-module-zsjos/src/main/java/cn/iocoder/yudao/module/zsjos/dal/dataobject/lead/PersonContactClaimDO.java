package cn.iocoder.yudao.module.zsjos.dal.dataobject.lead;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_person_contact_claim")
@KeySequence("zsjos_person_contact_claim_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class PersonContactClaimDO extends TenantBaseDO {
    @TableId private Long id;
    private String contactValue;
    private Long personId;
    private String reservationKey;
}
