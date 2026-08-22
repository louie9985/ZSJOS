package cn.iocoder.yudao.module.zsjos.dal.dataobject.studentops;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("zsjos_media_student_talk_record")
public class MediaStudentTalkRecordDO extends TenantBaseDO {
    @TableId private Long id;
    private Long studentPersonId;
    private Long accountId;
    private Long operatorUserId;
    private String content;
    private String attachmentFileIdsJson;
    private LocalDateTime occurredAt;
}
