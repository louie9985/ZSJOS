package cn.iocoder.yudao.module.zsjos.dal.dataobject.product;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_product_attr")
@KeySequence("zsjos_product_attr_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class ZsjosProductAttrDO extends TenantBaseDO {
    @TableId private Long id;
    private Long spuId;
    private String attrKey;
    private String attrName;
    private Boolean required;
    private Integer sort;
    private Integer status;
}
