package cn.iocoder.yudao.module.eam.dal.dataobject.asset;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("eam_public_edit_audit")
@KeySequence("eam_public_edit_audit_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class EamPublicEditAuditDO extends BaseDO {
    @TableId private Long id;
    private Long assetId;
    private Long employeeId;
    private String clientIp;
    private String resultCode;
    private String failureReason;
}
