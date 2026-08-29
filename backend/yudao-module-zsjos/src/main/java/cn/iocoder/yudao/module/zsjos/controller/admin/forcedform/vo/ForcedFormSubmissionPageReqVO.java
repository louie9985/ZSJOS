package cn.iocoder.yudao.module.zsjos.controller.admin.forcedform.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;

@Data
public class ForcedFormSubmissionPageReqVO extends PageParam {

    private Long formId;
    private Long userId;
    private String platform;

}
