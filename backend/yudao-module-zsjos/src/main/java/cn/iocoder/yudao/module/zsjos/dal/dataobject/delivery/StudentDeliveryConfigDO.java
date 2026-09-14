package cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_student_delivery_config")
@KeySequence("zsjos_student_delivery_config_seq")
@Data @EqualsAndHashCode(callSuper = true)
public class StudentDeliveryConfigDO extends TenantBaseDO {
    @TableId private Long id; private Integer version;
    private Integer s0Days; private Integer s1Days; private Integer s2Days;
    private Integer s3Days; private Integer s4Days; private Integer s5Days; private Integer s6Days;
    private Boolean enabled;
}
