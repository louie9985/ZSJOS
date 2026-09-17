package cn.iocoder.yudao.module.zsjos.dal.dataobject.positioning;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
@Data @Accessors(chain = true) @EqualsAndHashCode(callSuper = true)
@TableName("zsjos_positioning_service_card")
public class PositioningServiceCardDO extends TenantBaseDO {
    @TableId private Long id;
    private Long serviceRelationId; private Long studentPersonId; private Long cardId; private Integer version;
}
