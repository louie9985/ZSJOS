package cn.iocoder.yudao.module.zsjos.controller.admin.userrelation.vo.scene;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class UserRelationScenePageReqVO extends PageParam {

    private String name;
    private String code;
    private Integer status;

}
