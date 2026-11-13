package cn.iocoder.yudao.module.zsjos.dal.dataobject.versionupdate;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("zsjos_version_update")
public class VersionUpdateDO extends TenantBaseDO {
    @TableId private Long id;
    private String title;
    private String content;
    private String status;
    private String targetUserIds;
    private String attachmentIds;
    private String inlineFileIds;
    private Long publisherId;
    private String publisherName;
    private LocalDateTime publishTime;
}
