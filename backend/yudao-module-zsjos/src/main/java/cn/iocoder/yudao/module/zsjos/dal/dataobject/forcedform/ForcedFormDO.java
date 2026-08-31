package cn.iocoder.yudao.module.zsjos.dal.dataobject.forcedform;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_forced_form")
@Data
@EqualsAndHashCode(callSuper = true)
public class ForcedFormDO extends TenantBaseDO {

    private Long id;
    private String name;
    private String description;
    private String status;
    private String fieldsJson;
    private Integer version;
    private Long currentVersionId;

}
