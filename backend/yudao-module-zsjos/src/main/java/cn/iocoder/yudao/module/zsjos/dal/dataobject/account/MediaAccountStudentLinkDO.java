package cn.iocoder.yudao.module.zsjos.dal.dataobject.account;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_media_account_student_link")
@KeySequence("zsjos_media_account_student_link_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class MediaAccountStudentLinkDO extends TenantBaseDO {
    @TableId private Long id;
    private Long accountId;
    private Long studentPersonId;
    private String status;
    private String reason;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private Long operatedByUserId;
}
