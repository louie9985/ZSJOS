package cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@TableName("zsjos_student_delivery_plan")
@KeySequence("zsjos_student_delivery_plan_seq")
@Data @EqualsAndHashCode(callSuper = true)
public class StudentDeliveryPlanDO extends TenantBaseDO {
    @TableId private Long id; private Long studentPersonId; private Long accountId; private Long serviceRelationId;
    private Long directorUserId; private LocalDateTime accountOpenedAt; private Integer configVersion;
    private String status; private Integer version;
}
