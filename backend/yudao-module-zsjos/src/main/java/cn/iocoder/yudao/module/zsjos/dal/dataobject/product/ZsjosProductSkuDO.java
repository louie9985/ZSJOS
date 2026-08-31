package cn.iocoder.yudao.module.zsjos.dal.dataobject.product;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@TableName("zsjos_product_sku")
@KeySequence("zsjos_product_sku_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class ZsjosProductSkuDO extends TenantBaseDO {
    @TableId private Long id;
    private Long spuId;
    private String skuRef;
    private String skuName;
    private String attrValuesJson;
    private String attrValuesHash;
    private BigDecimal price;
    private Integer status;
    private Integer sort;
    private String remark;
}
