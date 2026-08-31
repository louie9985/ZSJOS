package cn.iocoder.yudao.module.zsjos.dal.dataobject.advancedfilter;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_advanced_filter_template")
@KeySequence("zsjos_advanced_filter_template_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class AdvancedFilterTemplateDO extends TenantBaseDO {
    @TableId
    private Long id;
    private String scene;
    private String pageKey;
    private String scope;
    private Long ownerUserId;
    private String name;
    private String filterJson;
    private Integer sort;
    private Boolean enabled;
    private Boolean defaultTemplate;
    private Integer version;
}
