package cn.iocoder.yudao.module.zsjos.dal.dataobject.userrelation;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_user_relation_scene")
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
    private String sourcePostCode;
    private String targetPostCode;
    private String targetEligibilityType;
    private String targetPermissionCode;
    private Integer status;
    private String remark;

}
