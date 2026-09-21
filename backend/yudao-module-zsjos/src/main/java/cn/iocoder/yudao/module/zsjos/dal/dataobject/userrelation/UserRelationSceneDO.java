package cn.iocoder.yudao.module.zsjos.dal.dataobject.userrelation;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.util.List;
import lombok.EqualsAndHashCode;

@TableName(value = "zsjos_user_relation_scene", autoResultMap = true)
@KeySequence("zsjos_user_relation_scene_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class UserRelationSceneDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String name;
    private String code;
    private String sourceLabel;
    private String targetLabel;
    private String sourceType;
    private String sourcePostCode;
    private String targetPostCode;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> sourcePostCodes;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> targetPostCodes;

    private String targetEligibilityType;
    private String targetPermissionCode;
    private Integer status;
    private String remark;

    // Null arrays identify legacy rows; an explicit empty array must never revive old eligibility.
    public List<String> getSourcePostCodes() {
        return sourcePostCodes != null ? sourcePostCodes
                : sourcePostCode == null || sourcePostCode.isBlank() ? List.of() : List.of(sourcePostCode);
    }

    public List<String> getTargetPostCodes() {
        return targetPostCodes != null ? targetPostCodes
                : targetPostCode == null || targetPostCode.isBlank() ? List.of() : List.of(targetPostCode);
    }
}
