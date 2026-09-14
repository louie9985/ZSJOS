package cn.iocoder.yudao.module.zsjos.dal.dataobject.account;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
@Data
@EqualsAndHashCode(callSuper=true)
@TableName("zsjos_media_account_profile_entry")
@KeySequence("zsjos_media_account_profile_entry_seq")
public class MediaAccountProfileEntryDO extends TenantBaseDO {
    @TableId private Long id;
    private Long accountId;
    private Long operatedByUserId;
    private String operatedByName;
    private String kind;
    private String fieldKey;
    private String title;
    private String content;
    private String snapshotJson;
    private String filesJson;
    private String idempotencyKey;
    private String fingerprint;
    private Integer resultVersion;
}
