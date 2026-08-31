package cn.iocoder.yudao.module.eam.dal.dataobject.asset;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("eam_public_edit_code")
@KeySequence("eam_public_edit_code_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class EamPublicEditCodeDO extends BaseDO {
    @TableId private Long id;
    private Long employeeId;
    private Long userId;
    private String encryptedCode;
    private String codeHmac;
    private Integer status;
}
