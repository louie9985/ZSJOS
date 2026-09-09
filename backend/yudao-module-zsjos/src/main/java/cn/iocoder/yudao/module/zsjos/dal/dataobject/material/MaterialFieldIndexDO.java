package cn.iocoder.yudao.module.zsjos.dal.dataobject.material;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("zsjos_material_field_index")
@KeySequence("zsjos_material_field_index_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class MaterialFieldIndexDO extends TenantBaseDO {
    @TableId private Long id;
    private Long materialVersionId;
    private String fieldKey;
    private Integer groupIndex;
    private String valueCode;
    private String labelSnapshot;
    private String textValue;
    private BigDecimal numberValue;
    private LocalDate dateValue;
    private LocalDateTime datetimeValue;
}
