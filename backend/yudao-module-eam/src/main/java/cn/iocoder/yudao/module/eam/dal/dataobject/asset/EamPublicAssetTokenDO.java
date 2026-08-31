package cn.iocoder.yudao.module.eam.dal.dataobject.asset;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("eam_public_asset_token")
@KeySequence("eam_public_asset_token_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class EamPublicAssetTokenDO extends BaseDO {
    @TableId private Long id;
    private Long assetId;
    private String tokenHash;
    private Integer status;
    private LocalDateTime revokedAt;
    private Integer version;
}
