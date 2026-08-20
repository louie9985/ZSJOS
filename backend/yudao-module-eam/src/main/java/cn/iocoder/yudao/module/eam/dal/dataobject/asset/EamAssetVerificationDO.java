package cn.iocoder.yudao.module.eam.dal.dataobject.asset;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("eam_asset_verification")
@KeySequence("eam_asset_verification_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class EamAssetVerificationDO extends BaseDO {

    @TableId
    private Long id;
    private Long assetId;
    private String result;
    private String labelStatus;
    private Long verifierUserId;
    private String verifierNameSnapshot;
    private LocalDateTime verifiedAt;
    private String remark;
    private Long importBatchId;
}
