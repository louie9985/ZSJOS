package cn.iocoder.yudao.module.zsjos.controller.admin.forcedform.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class ForcedFormSendReqVO {

    @NotBlank
    private String scopeType;
    private List<Long> userIds;
    private List<Long> deptIds;
    private List<Long> postIds;

}
