package cn.iocoder.yudao.module.zsjos.dal.dataobject.forcedform;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_forced_form_batch")
@Data
@EqualsAndHashCode(callSuper = true)
public class ForcedFormBatchDO extends TenantBaseDO {

    private Long id;
    private Long formId;
    private Long versionId;
    private String scopeType;
    private String scopeConfigJson;
    private String status;
    private LocalDateTime sentAt;

}
