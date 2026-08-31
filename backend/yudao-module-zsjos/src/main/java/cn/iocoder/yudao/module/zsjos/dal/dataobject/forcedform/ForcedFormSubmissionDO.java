package cn.iocoder.yudao.module.zsjos.dal.dataobject.forcedform;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_forced_form_submission")
@Data
@EqualsAndHashCode(callSuper = true)
public class ForcedFormSubmissionDO extends TenantBaseDO {

    private Long id;
    private Long formId;
    private Long versionId;
    private Long userId;
    private String fieldsSnapshotJson;
    private String answersJson;
    private String dictSnapshotJson;
    private String platform;

}
