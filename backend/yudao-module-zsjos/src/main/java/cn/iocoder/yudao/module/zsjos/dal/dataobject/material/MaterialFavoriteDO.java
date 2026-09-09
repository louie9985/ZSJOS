package cn.iocoder.yudao.module.zsjos.dal.dataobject.material;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_material_favorite")
@KeySequence("zsjos_material_favorite_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class MaterialFavoriteDO extends TenantBaseDO {
    @TableId private Long id;
    private Long materialId;
    private Long userId;
    private Boolean active;
    private java.time.LocalDateTime favoritedAt;
    private java.time.LocalDateTime unfavoritedAt;
    private Integer version;
}
