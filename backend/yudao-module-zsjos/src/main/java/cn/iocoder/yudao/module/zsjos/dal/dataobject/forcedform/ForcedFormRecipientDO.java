package cn.iocoder.yudao.module.zsjos.dal.dataobject.forcedform;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_forced_form_recipient")
@Data
@EqualsAndHashCode(callSuper = true)
public class ForcedFormRecipientDO extends TenantBaseDO {

    private Long id;
    private Long batchId;
    private Long formId;
    private Long userId;
    private String nicknameSnapshot;
    private String deptSnapshot;
    private String postSnapshot;
    private String source;
    private String status;
    private LocalDateTime completedAt;
    private Long submissionId;

}
