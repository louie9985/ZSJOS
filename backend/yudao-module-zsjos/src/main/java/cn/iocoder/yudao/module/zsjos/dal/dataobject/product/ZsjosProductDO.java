package cn.iocoder.yudao.module.zsjos.dal.dataobject.product;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_product")
@KeySequence("zsjos_product_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class ZsjosProductDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long categoryId;
    private String productRef;
    private String name;
    private String subtitle;
    private String description;
    private String targetAudience;
    private String studyDuration;
    private String studyMode;
    private String coverImage;
    private Integer status;
    private Integer sort;
    private String remark;
}
