package cn.iocoder.yudao.module.zsjos.dal.dataobject.material;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_material_type")
@KeySequence("zsjos_material_type_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class MaterialTypeDO extends TenantBaseDO {
    @TableId private Long id;
    private String name;
    private String code;
    private String description;
    private Integer status;
    private Long currentSchemaVersionId;
    private String bpmProcessDefinitionKey;
    private Boolean allowManualCreate;
    private Boolean allowImport;
    private Boolean allowAutoCollect;
    private Boolean recommendationEnabled;
    private String recommendationConfigJson;
    private Integer version;
}
