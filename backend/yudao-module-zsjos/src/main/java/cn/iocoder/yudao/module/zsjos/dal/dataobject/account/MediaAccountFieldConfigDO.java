package cn.iocoder.yudao.module.zsjos.dal.dataobject.account;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_media_account_field_config")
@KeySequence("zsjos_media_account_field_config_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class MediaAccountFieldConfigDO extends TenantBaseDO {
    @TableId private Long id;
    private Integer versionNo;
    private String status;
    private String fieldsJson;
    private LocalDateTime publishedAt;
    private Integer version;
}
