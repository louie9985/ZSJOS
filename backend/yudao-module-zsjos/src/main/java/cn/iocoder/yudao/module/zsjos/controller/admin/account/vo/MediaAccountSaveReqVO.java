package cn.iocoder.yudao.module.zsjos.controller.admin.account.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class MediaAccountSaveReqVO {
    private Long studentPersonId;
    private Long directorUserId;
    @NotBlank private String platformValue;
    @NotBlank private String platformLabelSnapshot;
    private String platformAccountId;
    private String nickname;
    private String leadDirection;
    private Map<String, Object> detailValues;
}
