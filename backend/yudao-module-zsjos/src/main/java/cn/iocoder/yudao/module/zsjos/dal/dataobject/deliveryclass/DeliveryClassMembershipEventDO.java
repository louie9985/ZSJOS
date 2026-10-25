package cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper=true)
@TableName("zsjos_delivery_class_membership_event")
public class DeliveryClassMembershipEventDO extends TenantBaseDO {
    @TableId private Long id;
    private Long classId;
    private Long sourceGroupId;
    private Long targetGroupId;
    private Long operatorUserId;
}
