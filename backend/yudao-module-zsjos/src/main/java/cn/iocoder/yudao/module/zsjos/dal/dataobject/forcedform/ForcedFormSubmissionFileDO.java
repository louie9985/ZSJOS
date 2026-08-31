package cn.iocoder.yudao.module.zsjos.dal.dataobject.forcedform;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_forced_form_submission_file")
@Data
@EqualsAndHashCode(callSuper = true)
public class ForcedFormSubmissionFileDO extends TenantBaseDO {

    private Long id;
    private Long formId;
    private Long versionId;
    private Long userId;
    private Long submissionId;
    private String fieldKey;
    private Long infraFileId;
    private String uploadToken;
    private String fileName;
    private Long fileSize;
    private String contentType;
    private String status;

}
