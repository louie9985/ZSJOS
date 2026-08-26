package cn.iocoder.yudao.module.zsjos.controller.admin.userrelation.vo.scene;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserRelationSceneRespVO {

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
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
