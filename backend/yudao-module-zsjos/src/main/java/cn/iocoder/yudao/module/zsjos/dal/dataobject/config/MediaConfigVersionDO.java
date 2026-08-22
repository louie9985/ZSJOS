package cn.iocoder.yudao.module.zsjos.dal.dataobject.config;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_media_config_version")
@KeySequence("zsjos_media_config_version_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class MediaConfigVersionDO extends TenantBaseDO {
    @TableId private Long id;
    private Integer versionNo;
    private String status;
    private String approvalConfigJson;
    private String stageChecklistJson;
    private String thresholdConfigJson;
    private String accountGradeRulesJson;
    private LocalDateTime publishedAt;
    private Integer version;
}
