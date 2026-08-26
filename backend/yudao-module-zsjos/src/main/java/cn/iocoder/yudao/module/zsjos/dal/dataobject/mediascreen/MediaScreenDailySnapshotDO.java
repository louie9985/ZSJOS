package cn.iocoder.yudao.module.zsjos.dal.dataobject.mediascreen;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDate;

@TableName("zsjos_media_screen_daily_snapshot")
@KeySequence("zsjos_media_screen_daily_snapshot_seq")
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class MediaScreenDailySnapshotDO extends TenantBaseDO {
    @TableId private Long id;
    private LocalDate snapshotDate;
    private Long supervisorId;
    private String departmentName;
    private Long memberId;
    private String memberName;
    private Integer submittedCount;
    private Integer validCount;
    private Integer partTimeSubmittedCount;
    private Integer partTimeValidCount;
}
