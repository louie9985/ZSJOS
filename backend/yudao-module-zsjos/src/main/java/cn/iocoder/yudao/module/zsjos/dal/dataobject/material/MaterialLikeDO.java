package cn.iocoder.yudao.module.zsjos.dal.dataobject.material;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_material_like")
@KeySequence("zsjos_material_like_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class MaterialLikeDO extends TenantBaseDO {
    @TableId private Long id;
    private Long materialId;
    private Long userId;
    private Boolean active;
    private java.time.LocalDateTime likedAt;
    private java.time.LocalDateTime unlikedAt;
    private Integer version;
}
