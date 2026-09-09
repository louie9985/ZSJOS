package cn.iocoder.yudao.module.zsjos.dal.dataobject.material;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_material_dimension")
@KeySequence("zsjos_material_dimension_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class MaterialDimensionDO extends TenantBaseDO {
    @TableId private Long id;
    private Long materialVersionId;
    private String dimensionKey;
    private String dimensionValue;
    private String labelSnapshot;
    private Boolean unlimited;
}
