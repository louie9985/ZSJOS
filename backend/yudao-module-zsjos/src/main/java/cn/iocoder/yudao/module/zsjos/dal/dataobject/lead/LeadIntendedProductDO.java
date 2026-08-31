package cn.iocoder.yudao.module.zsjos.dal.dataobject.lead;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_lead_intended_product")
@KeySequence("zsjos_lead_intended_product_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class LeadIntendedProductDO extends TenantBaseDO {
    @TableId private Long id;
    private Long leadId;
    private String productRef;
    private String productNameSnapshot;
    private String spuRef;
    private String spuNameSnapshot;
    private String skuRef;
    private String skuNameSnapshot;
    private String selectedAttrValuesJson;
    private java.math.BigDecimal priceSnapshot;
    private Boolean spuUnknown;
    private Boolean skuUnknown;
    private Long categoryId;
    private String categoryNameSnapshot;
    private String categoryPathSnapshot;
    private Long level1CategoryId;
    private String level1CategoryNameSnapshot;
    private Long level2CategoryId;
    private String level2CategoryNameSnapshot;
    private Boolean isPrimary;
    private Integer sort;
}
