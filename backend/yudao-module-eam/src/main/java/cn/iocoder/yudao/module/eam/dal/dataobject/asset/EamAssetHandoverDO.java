package cn.iocoder.yudao.module.eam.dal.dataobject.asset;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("eam_asset_handover")
@KeySequence("eam_asset_handover_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class EamAssetHandoverDO extends BaseDO {

    @TableId
    private Long id;
    private Long assetId;
    private String content;
    private Long fromUserId;
    private Long toUserId;
    private LocalDateTime handoverTime;
    private String remark;
    private Long importBatchId;
}
