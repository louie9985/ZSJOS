package cn.iocoder.yudao.module.zsjos.dal.dataobject.material;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_material")
@KeySequence("zsjos_material_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class MaterialDO extends TenantBaseDO {
    @TableId private Long id;
    private String materialNo;
    private Long materialTypeId;
    private String title;
    private String coverSnapshotJson;
    private String summary;
    private String source;
    private String sourceBusinessId;
    private String sourceBusinessVersionId;
    private String status;
    private Long currentEffectiveVersionId;
    private Long currentDraftVersionId;
    private Long ownerUserId;
    private Long likeCount;
    private Long favoriteCount;
    private Long referenceCount;
    private Boolean pinned;
    private Integer priority;
    private String disabledReason;
    private LocalDateTime disabledAt;
    private Long disabledByUserId;
    private Integer version;
}
