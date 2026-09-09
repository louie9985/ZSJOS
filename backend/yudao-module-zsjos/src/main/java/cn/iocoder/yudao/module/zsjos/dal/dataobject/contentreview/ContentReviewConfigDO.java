package cn.iocoder.yudao.module.zsjos.dal.dataobject.contentreview;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_content_review_config")
@KeySequence("zsjos_content_review_config_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class ContentReviewConfigDO extends TenantBaseDO {
    @TableId private Long id;
    private String processDefinitionKey;
    private String directorTaskKey;
    private String finalTaskKey;
    private String productionMaterialTypeCode;
    private String materialFieldMappingJson;
    private String materialDefaultValuesJson;
    private Integer version;
}
