package cn.iocoder.yudao.module.zsjos.dal.dataobject.product;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_product_attr_value")
@KeySequence("zsjos_product_attr_value_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class ZsjosProductAttrValueDO extends TenantBaseDO {
    @TableId private Long id;
    private Long attrId;
    private String value;
    private String label;
    private Integer sort;
    private Integer status;
}
