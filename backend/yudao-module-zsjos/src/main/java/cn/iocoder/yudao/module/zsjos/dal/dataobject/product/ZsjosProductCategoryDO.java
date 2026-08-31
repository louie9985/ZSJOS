package cn.iocoder.yudao.module.zsjos.dal.dataobject.product;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;

@TableName("zsjos_product_category")
@KeySequence("zsjos_product_category_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class ZsjosProductCategoryDO extends TenantBaseDO {
    @TableId private Long id;
    private Long parentId;
    private Integer level;
    private String name;
    private BigDecimal defaultValidCashbackAmount;
    private BigDecimal defaultDealCashbackRate;
    private Integer status;
    private Integer sort;
    private String remark;
}
